(ns skrejp.crawl-planner-test
  (:require [skrejp.crawl-planner.component :as crawler])
  (:require [expectations :refer :all]))

(let
  [feed {:feed-type "rss_2.0",
         :entries
           [{:link "http://example.com/foo.html", :title "Foo"},
            {:uri  "http://example.com/bar.html", :title "Bar"}]}
   scrape-reqs (into [] crawler/mapcat-feed-to-docs [feed])]
  (expect 2 (count scrape-reqs))
  (expect "http://example.com/foo.html" (-> scrape-reqs first  :url))
  (expect "http://example.com/bar.html" (-> scrape-reqs second :url))
  (expect "Foo" (-> scrape-reqs first  :title))
  (expect "Bar" (-> scrape-reqs second :title)))
