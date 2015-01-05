(ns skrejp.app
  (:require [skrejp.scraper :as scraper])
  (:require [clojurewerkz.urly.core :as urly])
  (:require [com.stuartsierra.component :as component])
  (:require [clojure.core.async :as async :refer [<!!]])
  (:require [clojure.string :refer [lower-case]])
  (:require [clj-time.core :as t])
  (:require [skrejp.system :as system])
  (:require [clojure.tools.cli :refer [cli]])
  (:import  [org.apache.commons.daemon Daemon DaemonContext])
  (:gen-class :implements [org.apache.commons.daemon.Daemon]))

(defn parse-int [s] (Integer/parseInt s))

(def month-signs {"jan" 1 "feb" 2 "már" 3 "ápr"  4 "máj"  5 "jún"  6
                  "júl" 7 "aug" 8 "sze" 9 "okt" 10 "nov" 11 "dec" 12})

(defn parse-time-str [str]
  (try
    (let
      [[_ xy xmo xd _ xh xmi]
       (re-find #"(\d{4})\. (\w{3})\w+ (\d{1,2})\.([^\d]+(\d{1,2}):(\d{1,2}))?" str)
       [y d h mi] (map parse-int (take-while (complement nil?) [xy xd xh xmi]))
       mo (-> xmo lower-case month-signs)
       time-params (take-while (complement nil?) [y mo d h mi])]
      (when (>= (count time-params) 3)
        (apply t/date-time time-params)))
    (catch Exception _ nil)))

(def config-options
  {:feeds
     ["http://ujszo.com/rss.xml"
      "http://vasarnap.ujszo.com/rss.xml"
      "http://www.bumm.sk/rss/rss.xml"
      "http://www.felvidek.ma/?format=feed&type=rss"
      "http://www.parameter.sk/rss.xml"
      "http://www.hirek.sk/rss/hirek.xml"]
   :scraper-defs
     {:shared            {:source  #(-> % :url urly/url-like urly/host-of)}
      "www.bumm.sk"      {:title   [:h2#page_title]
                          :summary [:div.page_lead]
                          :content [:div.page_body]
                          :published_at
                          #(-> % (scraper/extract-tag [:div.page_public_date]) parse-time-str)}
      "felvidek.ma"      {:title   [:article :header.article-header :h1.article-title :a]
                          :content [:section.article-content]
                          :published_at
                          #(-> % (scraper/extract-tag [:dd.create]) parse-time-str)}
      "ujszo.com"        {:title   [:div.node.node-article :h1]
                          :loc     [:div.node.node-article :div.field-name-field-lead :span.place]
                          :summary [:div.node.node-article :div.field-name-field-lead :p]
                          :content [:div.node.node-article :div.field-name-body]
                          :published_at
                          #(-> % (scraper/extract-tag [:div.article-header]) parse-time-str)}
      "www.parameter.sk" {:title   [:div#content :h1]
                          :summary [:div#content :div.field-name-field-lead]
                          :content [:div#content :div.field-name-body]
                          :published_at
                          #(-> % (scraper/extract-tag [:div.article-header]) parse-time-str)}
      "www.hirek.sk"     {:title   [:span.tcikkcim]
                          :summary [:span#tcikkintro]
                          :content [:div#tcikktext]
                          :published_at
                          #(-> % (scraper/extract-tag [:span.tcikkinfo]) parse-time-str)}
      "vasarnap.ujszo.com" "ujszo.com"}
   :http-req-opts
     {:timeout    200 ; ms
      :user-agent "User-Agent-string"
      :headers    {"X-Header" "Value"}}
   :storage {:es {:url         "http://localhost:9200"
                  :index-name  "mediaspajz_development_articles" ; "mediaspajz_test"
                  :entity-name "article"}}})

(def scraper-system (system/build-scraper-system config-options))

(defn start-scraper-system
  "Starts the scraper system."
  []
  (alter-var-root (var scraper-system) component/start))

(defn stop-scraper-system
  "Stops the passed in system"
  []
  (alter-var-root (var scraper-system) component/stop))

(def state (atom {}))

(defn init [args]
  (swap! state assoc :running true))

(defn start []
  (while (:running @state)
    (println "tick")
    (Thread/sleep 2000)))

(defn stop []
  (swap! state assoc :running false))

;; Daemon implementation

(defn -init [this ^DaemonContext context]
  (init (.getArguments context)))

(defn -start [this]
  (future (start)))

(defn -stop [this]
  (stop))

(defn -destroy [this])

(defn -main [& args]
  (let [[opts args banner]
        (cli args
             ["-h" "--help"   "Print this help"                :default false :flag true]
             ["-e" "--exec"   "Runs retrieval for <n> seconds" :default false :parse-fn #(Integer. %)]
             ["-d" "--daemon" "Execute in background"          :default false :flag true])]
    (when (:help opts)
      (println banner))
    (when (:exec opts)
      (start-scraper-system)
      (<!! (async/timeout (* (:exec opts) 1000)))
      (stop-scraper-system))
    (when (:daemon opts)
      (init args)
      (start))))
