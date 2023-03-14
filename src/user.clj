(ns user
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as lutil]
            [integrant.core :as ig]
            [integrant.repl :as repl]
            [io.pedestal.http :as http]))

(def ^:private system-config
  {:http {}})

(repl/set-prep! (constantly system-config))

(def ^:private the-db
  (atom [{:id (random-uuid)
          :name "John"
          :added-by nil}
         {:id (random-uuid)
          :name "Jane"
          :added-by nil}]))

(defn- add-person
  [db {:keys [clientMutationId name]}]
  (if-let [existing-person (first (filter #(= (:added-by %) clientMutationId) @db))]
    existing-person
    (let [new-person {:id (random-uuid)
                      :name name
                      :added-by clientMutationId}]
      (swap! db conj new-person)
      new-person)))

(defn- resolvers-map
  []
  {:mutations/add-person (fn [_ctx
                              {{:keys [clientMutationId name]} :input}
                              _value]
                           {:clientMutationId clientMutationId
                            :result (add-person the-db {:clientMutationId clientMutationId
                                                        :name name})})
   :queries/people (fn [_ctx _args _value]
                     @the-db)})

(defn- load-schema
  []
  (-> (io/resource "schema.edn")
      (slurp)
      (edn/read-string)
      (lutil/attach-resolvers (resolvers-map))
      (schema/compile)))

(def ^:private schema (load-schema))

(defn- http-server
  []
  (-> (lp/default-service schema nil)
      http/create-server))

(defmethod ig/init-key :http
  [_ _]
  (http/start (http-server)))

(defmethod ig/halt-key! :http
  [_ server]
  (http/stop server))
