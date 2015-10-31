(ns adventure.txt
  (:require [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.nodes         :as nn]
            [clojurewerkz.neocons.rest.labels        :as nl]
            [clojurewerkz.neocons.rest.index         :as ni]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.cypher        :as cy]

            [liberator.core         :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response     :refer [redirect]]
            [compojure.core         :refer [defroutes GET]]))

(def connection-string "http://localhost:7474/db/data/")

(def connection (nr/connect connection-string))

(def initial-seed-data
  [{ :title "Your Adventure Ends Here"
     :sections [{ :content "Make a decision"
                  :options { "Do one thing" 1
                             "Do another"   2 }}
                { :content "You did one thing. Want to start again?"
                  :options { "Yes" 0
                             "No"  2 }}
                { :content "Your adventure ends here" }]}])

(defn initialise
  "Clears the existing data in the graph database and refreshes it based on
   the set of initial data defined above"
  []
  (cy/query connection "START r=relationship(*) DELETE r;")
  (cy/query connection "START n=node(*) DELETE n;")
  (doseq [story initial-seed-data]
    (let [story-node     (nn/create connection { :title (story :title) })
          create-section (fn [section]
                           (let [section-node (nn/create connection { :content (section :content) })]
                             (nl/add connection section-node "section")
                             section-node))
          section-nodes  (->> (story :sections) (map create-section) doall)
          sections       (zipmap (story :sections) section-nodes)]
      (nl/add connection story-node "story")
      (nrl/create connection story-node (first section-nodes) :decision)
      (doseq [[section-definition section-node] sections
              :when (section-definition :options)]
        (doseq [[content index] (section-definition :options)
                :let [end-node (nth section-nodes index)]]
          (nrl/create connection section-node end-node :decision { :content content }))))))

(defn get-all-stories []
  (let [stories (nl/get-all-nodes connection "story")]
    (map (fn [sn] { :id (:id sn) :title (-> sn :data :title) }) stories)))

(defn get-story [id]
  (let [story-node (nn/get connection id)
        start-node (-> (nn/traverse connection id
                        :relationships [{ :direction "out" :type :decision }]
                        :return-filter  {:language "builtin" :name "all_but_start_node"}
                        :max-depth 1) first)]
    { :id id
      :content (-> story-node :data :title)
      :options { "Begin" (:id start-node) } }))

(defn- build-section [node]
  (let [rels (nrl/outgoing-for connection node :types [:decision])
        opts (into {} (for [rel rels] [(-> rel :data :content) (:id rel)]))]
     { :id (:id node)
       :content (-> node :data :content)
       :options opts }) )

(defn start-story [id]
   (let [node (-> (nn/traverse connection id
                    :relationships [{ :direction "out" :type :decision }]
                    :return-filter  {:language "builtin" :name "all_but_start_node"}
                    :max-depth 1) first)]
     (build-section node)))

(defn make-decision [id]
  (let [rel  (nrl/get connection id)
        node-id (-> rel :end (clojure.string/split #"\/") last Integer/parseInt)
        node (nn/get connection node-id)]
    (build-section node)))

(defn- canonical-url [request]
  (str  (-> request :request :scheme name) "://" (-> request :request :headers (get "host"))))

(defn- link-options [request story-id outcome]
  (update-in outcome [:options]
    #(for [[opt-content opt-id] %]
      { :id opt-id
        :content opt-content
        :$decision-url (str (canonical-url request) "/story/" story-id "/decision/" opt-id) })))

(defn- link-story [request story-id outcome]
  (merge outcome { :$story-url (str (canonical-url request) "/story/" story-id ) }))

(defresource stories
  :available-media-types ["application/json"]
  :handle-ok (fn [r]
               (let [stories (get-all-stories)]
                 (map (fn [story] (merge story { :$story-url (str (canonical-url r) "/story/" (:id story)) }))  stories))))

(defresource story [id]
  :available-media-types ["application/json"]
  :handle-ok (fn [r]
               (let [story (get-story id)]
                 (->> story
                      (link-options r id)
                      (link-story r id)))))

(defresource decision [id decision]
  :available-media-types ["application/json"]
  :handle-ok (fn [r]
               (let [decision (make-decision decision)]
                 (->> decision
                     (link-options r id)
                     (link-story r id)))))

(defroutes app
  (GET "/stories" [] stories)
  (GET "/story/:id" [id] (story (Long/parseLong id)))
  (GET "/story/:story-id/decision/:id" [story-id id]
    (decision (Long/parseLong story-id) (Long/parseLong id))))

(def handler (-> app wrap-params))
