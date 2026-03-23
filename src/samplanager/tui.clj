(ns samplanager.tui
  "Terminal UI for samplanager using charm.clj.
   Uses a shared atom for scan progress — the view reads it on each
   spinner tick, so no custom message passing is needed."
  (:require [charm.core :as charm]
            [mokujin.log :as log]))

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

(def error-style
  (charm/style :fg charm/red
               :bold true))

;; -- Shared scan state (written by pipeline, read by view) --

(def scan-state
  "Atom holding scan progress. Updated by the pipeline future,
   read by the charm view function on each render tick."
  (atom {:phase :scanning
         :scan-count 0
         :checksum-count 0
         :checksum-total 0
         :report nil
         :error nil}))

;; -- Promise for coordinating shutdown --

(def ^:private exit-promise (promise))

;; -- Public state update API (called from core pipeline) --

(defn update-scan-progress!
  [n]
  (swap! scan-state assoc :scan-count n))

(defn scan-complete!
  [{:keys [count candidates]}]
  (log/info "tui: scan complete" {:count count :candidates candidates})
  (swap! scan-state assoc
         :phase :checksumming
         :scan-count count
         :checksum-total candidates))

(defn update-checksum-progress!
  [n]
  (swap! scan-state assoc :checksum-count n))

(defn done!
  [report]
  (log/info "tui: done")
  (swap! scan-state assoc
         :phase :done
         :report report)
  (deliver exit-promise :done))

(defn error!
  [message]
  (log/error "tui: error" {:message message})
  (swap! scan-state assoc
         :phase :error
         :error message)
  (deliver exit-promise :error))

;; -- Charm program (only handles spinner + quit) --

(defn- init
  [{:keys [dirs output-file]}]
  (let [[spinner spinner-cmd] (charm/spinner-init (charm/spinner :dots))]
    [{:dirs dirs
      :output-file output-file
      :spinner spinner}
     spinner-cmd]))

(defn- update-state
  [state msg]
  (cond
    (and (charm/key-press? msg)
         (or (charm/key-match? msg "q")
             (charm/key-match? msg "ctrl+c")))
    (do (log/info "user quit")
        (deliver exit-promise :quit)
        [state charm/quit-cmd])

    (charm/spinning? (:spinner state) msg)
    (let [[spinner cmd] (charm/spinner-update (:spinner state) msg)]
      [(assoc state :spinner spinner) cmd])

    :else [state nil]))

;; -- View helpers --

(defn- stat-line
  [label value]
  (str (charm/render label-style (str "  " label ": "))
       (charm/render value-style (str value))))

(defn- progress-bar
  [done total width]
  (let [pct (if (pos? total) (/ (double done) total) 0.0)
        filled (int (* width pct))
        empty (- width filled)
        pct-str (str (int (* 100 pct)) "%")]
    (str (charm/render ok-style (apply str (repeat filled "█")))
         (charm/render dim-style (apply str (repeat empty "░")))
         " " (charm/render value-style pct-str)
         (charm/render dim-style (str " (" done "/" total ")")))))

(defn- view-scanning
  [state scan]
  (str (charm/render title-style "samplanager")
       "\n\n"
       "  " (charm/spinner-view (:spinner state))
       " Scanning " (charm/render value-style
                                  (str (count (:dirs state)) " director"
                                       (if (= 1 (count (:dirs state))) "y" "ies")))
       " for audio files...\n"
       "\n"
       (stat-line "Audio files found" (:scan-count scan))
       "\n"))

(defn- view-checksumming
  [state scan]
  (str (charm/render title-style "samplanager")
       "\n\n"
       "  " (charm/spinner-view (:spinner state))
       " Checksumming candidates...\n"
       "\n"
       (stat-line "Audio files scanned" (:scan-count scan))
       "\n"
       "  " (progress-bar (:checksum-count scan)
                          (:checksum-total scan)
                          30)
       "\n"))

(defn- view-done
  [state scan]
  (let [{:keys [report]} scan
        dirs (:dirs-by-duplicates report)
        top-dirs (take 5 dirs)
        savings (:duplicate-size-human report)]
    (str (charm/render title-style "samplanager") " "
         (charm/render ok-style "✓ complete")
         "\n\n"
         (apply str (map (fn [d] (str (stat-line "Root" d) "\n")) (:dirs state)))
         "\n"
         (stat-line "Audio files" (:found-files-count report))
         "\n"
         (stat-line "Total size" (:total-size-human report))
         "\n\n"
         (charm/render title-style "  Duplicates")
         "\n"
         (stat-line "Duplicate groups" (:duplicate-groups-count report))
         "\n"
         (stat-line "Duplicate files" (:duplicate-files-count report))
         "\n"
         (stat-line "Potential savings" savings)
         "\n"
         (when (seq top-dirs)
           (str "\n"
                (charm/render title-style "  Top directories by duplicates")
                "\n"
                (apply str
                       (map (fn [[dir cnt]]
                              (str "  " (charm/render value-style (format "%4d" cnt))
                                   " " (charm/render dim-style dir) "\n"))
                            top-dirs))))
         "\n"
         (stat-line "Output written to" (:output-file state))
         "\n")))

(defn- view-error
  [_state scan]
  (str (charm/render title-style "samplanager") " "
       (charm/render error-style "✗ error")
       "\n\n"
       "  " (charm/render error-style (:error scan))
       "\n"))

(defn- view
  [state]
  (let [scan @scan-state]
    (case (:phase scan)
      :scanning (view-scanning state scan)
      :checksumming (view-checksumming state scan)
      :done (view-done state scan)
      :error (view-error state scan)
      "")))

;; -- Public API --

(defn run-tui
  "Starts the TUI. Non-blocking. Use await-exit to block until user quits."
  [{:keys [dirs output-file]}]
  (log/info "starting TUI" {:dirs dirs :output-file output-file})
  (charm/run-async
   {:init (fn [] (init {:dirs dirs
                        :output-file output-file}))
    :update update-state
    :view view
    :alt-screen true}))

(defn await-exit
  "Blocks until the TUI exits (user presses q)."
  []
  (deref exit-promise))
