(ns adventure.db
  (:require [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.nodes         :as nn]
            [clojurewerkz.neocons.rest.labels        :as nl]
            [clojurewerkz.neocons.rest.index         :as ni]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.cypher        :as cy]

            [adventure.data :refer [initial-seed-data]]))

(def ^:private connection-string "http://localhost:7474/db/data/")

(def ^:private connection (nr/connect connection-string))

(defn initialise
  "Clears the existing data in the graph database and refreshes it based on
   the set of initial data defined above"
  []
  (cy/query connection "START r=relationship(*) DELETE r;")
  (cy/query connection "START n=node(*) DELETE n;")
  (doseq [story initial-seed-data]
    (let [story-node     (nn/create connection (select-keys story [:title :description :author]))
          create-section (fn [section]
                           (let [section-node (nn/create connection { :content (section :content) })]
                             (nl/add connection section-node "section")
                             section-node))
          section-nodes  (->> (story :sections) (map create-section) doall)
          sections       (zipmap (story :sections) section-nodes)]
      (nl/add connection story-node "story")
      (nrl/create connection story-node (first section-nodes) :decision { :content "Begin" })
      (doseq [[section-definition section-node] sections
              :when (section-definition :options)]
        (doseq [[content index] (section-definition :options)
                :let [end-node (nth section-nodes index)]]
          (nrl/create connection section-node end-node :decision { :content content }))))))

(defn get-all-stories
  "Returns a list of all stories currently stored in the db"
  []
  (for [story (nl/get-all-nodes connection "story")]
    (assoc (:data story) :id (:id story))))

(defn- get-start-node [story-id]
  (first
    (nn/traverse connection story-id
      :relationships [{ :direction "out" :type :decision }]
      :return-filter  {:language "builtin" :name "all_but_start_node"}
      :max-depth 1)))

(defn- build-outcome [node]
  (let [relationships (nrl/outgoing-for connection node :types [:decision])
        options (map #(assoc (:data %) :id (:id %)) relationships)]
    (assoc (:data node) :id (:id node) :options options)))

(defn- is-story? [id]
  (let [query "MATCH (n:story) WHERE ID(n) = {id} RETURN n"
        result (cy/tquery connection query { :id id })]
    (not (empty? result))))

(defn get-story
  "Gets a single story based on the current story id. If no story has that
   id then return nil"
  [id]
  (when (is-story? id)
    (let [story-node (nn/get connection id) ]
      (build-outcome story-node))))

#_(defn- get-story-cypher []
  (let [query  "MATCH (s:story)-[d:decision]->(c:section) WHERE ID(s) = 4 RETURN s,d,c"
        result (first (cy/tquery connection query))
        keyed  (into {} (for [[k,v] result] [(keyword k) v]))
        { story :s decision :d c :c} keyed]
    ))


(defn start-story [id]
   (let [node (-> (nn/traverse connection id
                    :relationships [{ :direction "out" :type :decision }]
                    :return-filter  {:language "builtin" :name "all_but_start_node"}
                    :max-depth 1) first)]
     (build-outcome node)))

(defn make-decision [id]
  (let [rel  (nrl/get connection id)
        node-id (-> rel :end (clojure.string/split #"\/") last Integer/parseInt)
        node (nn/get connection node-id)]
    (build-outcome node)))
