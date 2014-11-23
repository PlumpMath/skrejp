(ns skrejp.retrieval
  (:require [skrejp.logger :as logger])
  (:require [com.stuartsierra.component :as component])
  (:require [org.httpkit.client :as http])
  (:require [feedparser-clj.core :as feeds]
            [clojure.core.async :as async])
  (:import  [java.io ByteArrayInputStream]))

(defn parse-feed-str [feed-s]
  (let
    [input-stream (ByteArrayInputStream. (.getBytes feed-s "UTF-8"))]
    (feeds/parse-feed input-stream) ) )

(defprotocol IRetrieval
  "## IRetrieval
  Defines methods for fetching pages.
  *fetch-page* is a transducer for fetching a page from a url.
  It expects the URL of the resource and it is pushing the fetch page to the channel it is applied on.
  If the error-fn is passed, it calls the error-fn function in case of an error."
  (fetch-page [this] [this error-fn])
  (fetch-feed [this]) )

(defrecord RetrievalComponent [http-opts]
  component/Lifecycle

  (start [this]
    (logger/info (:logger this) "Starting PageContentRetrieval")
    this)

  (stop [this]
    (logger/info (:logger this) "Stopping PageContentRetrieval")
    this)

  IRetrieval

  (fetch-page [this]
    (fn [doc c]
      (http/get (doc :url) (:http-opts this)
                (fn [{:keys [error] :as resp}]
                  (if-not error
                    (async/put! c (assoc doc :http-payload (resp :body))))
                  (async/close! c)))))

  (fetch-feed [this]
    (fn [xf]
      (fn ([] (xf)) ([result] (xf result))
        ([result url]
         (let
           [resp @(http/get url (:http-opts this))]
           (xf result (-> resp :body parse-feed-str))))))))

(defn build-component
  "Build a PageRetrieval component."
  [config-options]
  (map->RetrievalComponent (select-keys config-options [:http-req-opts])) )
