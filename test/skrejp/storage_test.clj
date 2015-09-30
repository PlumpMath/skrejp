(ns skrejp.storage-test
  (:require [skrejp.storage :as storage])
  (:require [skrejp.logger :as logger])
  (:require [expectations :refer :all])
  (:require [clojurewerkz.elastisch.rest.index :as esi])
  (:require [com.stuartsierra.component :as component])
  (:require [environ.core :refer [env]]))

(let
  [cmpnt (component/start (assoc
                            (storage/build-component
                              {:storage {:es {:url         (env :es-host)
                                              :index-name  (env :es-indexname)
                                              :entity-name (env :es-entityname)}}})
                            :logger (reify logger/ILogger (info [_ _]) (debug [_ _]))))
   doc-id  "http://example.com/foobar.html"
   _doc    (storage/store cmpnt {:id doc-id :title "Foo" :body "Bar" :http-payload "page body"})
   ret-doc (do (esi/flush (:es-conn cmpnt))
               (storage/get-doc cmpnt doc-id))]
  (expect "Foo" (ret-doc :title))
  (expect "Bar" (ret-doc :body))
  (expect false (contains? ret-doc :id))
  (expect false (contains? ret-doc :http-payload)))
; TODO: drop the testing index
