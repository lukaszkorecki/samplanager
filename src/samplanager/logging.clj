(ns samplanager.logging
  "Configures logback via mokujin to write human-readable logs to a file.
   Logs go to a local file only - never stdout - to avoid interfering with the TUI."
  (:require [mokujin.logback :as logback]))

(def ^:private log-file "samplanager.log")

(defn setup!
  "Initializes logback with a file appender. Call once at startup."
  []
  (logback/configure!
    {:config
     [:configuration
      [:import {:class "ch.qos.logback.classic.encoder.PatternLayoutEncoder"}]
      [:import {:class "ch.qos.logback.core.FileAppender"}]
      [:appender {:name "FILE" :class "FileAppender"}
       [:file log-file]
       [:append "false"]
       [:encoder {:class "PatternLayoutEncoder"}
        [:pattern "%date{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg %mdc%n"]]]
      [:root {:level "DEBUG"}
       [:appender-ref {:ref "FILE"}]]]
     :logger-filters {"org.jline" "WARN"
                      "charm" "WARN"}}))
