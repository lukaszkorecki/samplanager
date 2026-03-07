(ns samplanager.tui
  "Terminal UI for samplanager using charm.clj.
   Shows progress during scan and a stats summary when done."
  (:require [charm.core :as charm]
            [samplanager.report :as report]))

;; -- Styles --

(def title-style
  (charm/style :fg (charm/hex "#ff79c6")
               :bold true))

(def label-style
  (charm/style :fg (charm/hex "#8be9fd")))

(def value-style
  (charm/style :fg charm/white
               :bold true))

(def dim-style
  (charm/style :fg (charm/hex "#6272a4")))

(def ok-style
  (charm/style :fg charm/green
               :bold true))

(def warn-style
  (charm/style :fg charm/yellow
               :bold true))

;; -- State transitions --

(defn init
  "Initial TUI state."
  [{:keys [root-dir output-file]}]
  (let [[spinner spinner-cmd] (charm/spinner-init (charm/spinner :dots))]
    [{:phase :scanning
      :root-dir root-dir
      :output-file output-file
      :spinner spinner
      :scan-count 0
      :checksum-count 0
      :checksum-total 0
      :report nil
      :duplicates nil}
     spinner-cmd]))

(defn update-state
  [state msg]
  (cond
    ;; quit on q or ctrl+c
    (and (charm/key-press? msg)
         (or (charm/key-match? msg "q")
             (charm/key-match? msg "ctrl+c")))
    [state charm/quit-cmd]

    ;; spinner tick
    (charm/spinning? (:spinner state) msg)
    (let [[spinner cmd] (charm/spinner-update (:spinner state) msg)]
      [(assoc state :spinner spinner) cmd])

    ;; pipeline progress messages
    (and (map? msg) (= (:type msg) :scan-complete))
    [(assoc state
            :phase :checksumming
            :scan-count (:count msg)
            :checksum-total (:candidates msg))
     nil]

    (and (map? msg) (= (:type msg) :checksum-progress))
    [(assoc state :checksum-count (:count msg)) nil]

    (and (map? msg) (= (:type msg) :done))
    [(assoc state
            :phase :done
            :report (:report msg)
            :duplicates (:duplicates msg))
     nil]

    :else [state nil]))

;; -- View helpers --

(defn- stat-line
  [label value]
  (str (charm/render label-style (str "  " label ": "))
       (charm/render value-style (str value))))

(defn- view-scanning
  [state]
  (str (charm/render title-style "samplanager")
       "\n\n"
       "  " (charm/spinner-view (:spinner state))
       " Scanning " (charm/render value-style (:root-dir state)) " for audio files..."
       (when (pos? (:scan-count state))
         (str "\n" (stat-line "Files found" (:scan-count state))))))

(defn- view-checksumming
  [state]
  (let [done (:checksum-count state)
        total (:checksum-total state)
        pct (if (pos? total) (int (* 100 (/ (double done) total))) 0)
        bar-width 30
        filled (int (* bar-width (/ (double done) (max total 1))))
        empty (- bar-width filled)
        bar (str (charm/render ok-style (apply str (repeat filled "█")))
                 (charm/render dim-style (apply str (repeat empty "░"))))]
    (str (charm/render title-style "samplanager")
         "\n\n"
         "  " (charm/spinner-view (:spinner state))
         " Checksumming files...\n\n"
         "  " bar " " (charm/render value-style (str pct "%"))
         " (" done "/" total ")\n")))

(defn- view-done
  [state]
  (let [{:keys [report]} state
        dirs (:dirs-by-duplicates report)
        top-dirs (take 5 dirs)]
    (str (charm/render title-style "samplanager") " "
         (charm/render ok-style "✓ done")
         "\n\n"
         (stat-line "Root" (:root-dir state))
         "\n"
         (stat-line "Audio files" (:found-files-count report))
         "\n"
         (stat-line "Total size" (:total-size-human report))
         "\n\n"
         (charm/render title-style "  Duplicates")
         "\n"
         (stat-line "Groups" (:duplicate-groups-count report))
         "\n"
         (stat-line "Files" (:duplicate-files-count report))
         "\n"
         (stat-line "Wasted space" (:duplicate-size-human report))
         "\n"
         (when (seq top-dirs)
           (str "\n"
                (charm/render title-style "  Top directories by duplicates")
                "\n"
                (apply str
                       (map (fn [[dir cnt]]
                              (str (charm/render dim-style "  ")
                                   (charm/render value-style (str cnt))
                                   " " (charm/render dim-style dir) "\n"))
                            top-dirs))))
         "\n"
         (stat-line "Output written to" (:output-file state))
         "\n\n"
         (charm/render dim-style "  Press q to exit"))))

(defn view
  [state]
  (case (:phase state)
    :scanning (view-scanning state)
    :checksumming (view-checksumming state)
    :done (view-done state)
    ""))

;; -- Public API --

(defn run-tui
  "Runs the TUI. Returns a function to send messages into the program.
   Call the returned send-fn with maps like {:type :scan-complete ...}."
  [{:keys [root-dir output-file]}]
  (let [send-fn (charm/run-async
                  {:init (fn [] (init {:root-dir root-dir
                                       :output-file output-file}))
                   :update update-state
                   :view view
                   :alt-screen true})]
    send-fn))
