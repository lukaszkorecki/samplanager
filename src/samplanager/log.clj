(ns samplanager.log
  "Minimal file-based logger. Silent by default.
   Call (enable!) to write debug.log in CWD.")

(def ^:private enabled? (atom false))
(def ^:private log-writer (atom nil))

(defn enable!
  "Enables logging to debug.log in CWD."
  []
  (reset! enabled? true)
  (reset! log-writer (java.io.FileWriter. "debug.log" true)))

(defn- write!
  [level msg data]
  (when @enabled?
    (let [ts (java.time.LocalDateTime/now)
          line (str ts " [" level "] " msg
                    (when data (str " " (pr-str data)))
                    "\n")]
      (locking log-writer
        (.write ^java.io.FileWriter @log-writer line)
        (.flush ^java.io.FileWriter @log-writer)))))

(defn debug
  ([msg] (write! "DEBUG" msg nil))
  ([msg data] (write! "DEBUG" msg data)))

(defn info
  ([msg] (write! "INFO" msg nil))
  ([msg data] (write! "INFO" msg data)))

(defn error
  ([msg] (write! "ERROR" msg nil))
  ([msg data] (write! "ERROR" msg data))
  ([throwable msg data]
   (write! "ERROR" (str msg " " (.getMessage ^Throwable throwable))
           (assoc (or data {}) :exception (str (class throwable))))))
