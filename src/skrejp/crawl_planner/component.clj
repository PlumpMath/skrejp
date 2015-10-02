(ns skrejp.crawl-planner.component
  (:use [skrejp.crawl-planner.ann])
  (:require [skrejp.logger.ann :as logger])
  (:require [skrejp.retrieval.ann :as ret])
  (:require [clojure.core.async :as async :refer [go go-loop chan put! <! >!]])
  (:require [clojure.core.typed :as t])
  (:require [com.stuartsierra.component :as component]))

(t/tc-ignore
  (def mapcat-feed-to-docs
    (comp (mapcat :entries)
          (map (fn [entry]
                 (assoc (select-keys entry [:title])
                   :url (or (entry :link) (entry :uri))))))))

(t/ann-record CrawlPlannerComponent
              [feeds :- TFeedUrlVec
               planner-cmds :- TPlannerCmdVec
               cmd-c :- TPlannerCmdChan])

(defrecord CrawlPlannerComponent [feeds planner-cmds cmd-c]
  component/Lifecycle

  (start [this]
    (t/tc-ignore
      (logger/info (:logger this) "CrawlPlanner: Starting")
      (async/onto-chan (:cmd-c this) (or (:planner-cmds this) []) false)
      (go-loop [cmd (<! (:cmd-c this))]
        (logger/info (:logger this) (format "CrawlPlanner: Received: %s" cmd))
        (case cmd
          :plan-feeds (plan-feeds this))
        (recur (<! (:cmd-c this)))))
    this)

  (stop [this]
    (t/tc-ignore
      (logger/info (:logger this) "CrawlPlanner: Stopping"))
    this)

  ICrawlPlanner

  (plan-feeds [this]
    (t/tc-ignore
      (let
        [docs (into []
                    (comp (ret/fetch-feed (:page-retrieval this)) mapcat-feed-to-docs)
                    (:feeds this))]
        (async/onto-chan (-> this :scraper :doc-c) docs)))
    nil))

(t/defn cmd-chan
  [] :- TPlannerCmdChan
  (chan 16))

(t/defn build-component
  "Build a CrawlPlanner component."
  [conf-opts :- (t/HMap :mandatory {:feeds TFeedUrlVec
                                    :planner-cmds TPlannerCmdVec} :complete? false)] :- CrawlPlannerComponent
  (map->CrawlPlannerComponent {:feeds (:feeds conf-opts)
                               :planner-cmds (:planner-cmds conf-opts)
                               :cmd-c (cmd-chan)}))
