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
            [compojure.core         :refer [defroutes GET]]
            [adventure.db :refer :all]
            [ring.middleware.cors :refer [wrap-cors]]))


(defn- canonical-url [request]
  (str  (-> request :request :scheme name) "://" (-> request :request :headers (get "host"))))

(defn- link-option [request story-id option]
  (assoc option :$decision-url (str (canonical-url request) "/story/" story-id "/decision/" (:id option))))

(defn- link-options [request story-id outcome]
  (assoc outcome :options (map #(link-option request story-id %) (:options outcome))))

(defn- link-story [request story-id outcome]
  (assoc outcome :$story-url (str (canonical-url request) "/story/" story-id)))

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

(def handler (-> app wrap-params (wrap-cors :access-control-allow-origin [#"http://localhost:3001"]
                       :access-control-allow-methods [:get :put :post :delete])))
