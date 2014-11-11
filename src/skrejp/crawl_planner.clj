(ns skrejp.crawl-planner
  (:require [com.stuartsierra.component :as component])
  )

(defrecord CrawlPlannerComponent [page-retrieval scraper error-handling]
  component/Lifecycle

  (start [this]
    (println ";; Starting CrawlPlanner")
    this)

  (stop [this]
    (println ";; Stopping CrawlPlanner")
    this))

(defn build-component
  "Build a CrawlPlanner component."
  []
  (map->CrawlPlannerComponent {})
  )