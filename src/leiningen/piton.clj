(ns leiningen.piton
  (:require [clojure.java.io :as io]
            [leiningen.core.eval :refer [eval-in-project]]))

(def ^:private piton-opts (atom {}))

(defn- set-piton-opts
  [project]
  (reset! piton-opts
          (merge
           {:mig-path "sql/migrations"
            :seed-path "sql/seeds"}
           (:piton project {}))))

(defn scan-piton-dir
  "Go through list of files and make a vector of maps
  expected by the piton lib."
  [path]
  (mapv
   (fn [file] (.getName file))
   (.listFiles (io/file (str "resources/" path)))))

(defn- make-piton-file
  "When called, it will generate a new .piton file to
  track the migrations and seeds"
  []
  (spit
   "resources/piton.edn"
   (prn-str
    {:mig-path (:mig-path @piton-opts)
     :seed-path (:seed-path @piton-opts)
     :migrations (scan-piton-dir (:mig-path @piton-opts))
     :seeds (scan-piton-dir (:seed-path @piton-opts))})))

(defn- fresh-file [path file-name]
  (let  [gen-name (str (quot (System/currentTimeMillis) 1000)
                       "-" file-name ".sql")
         gen-path (str
                   (path @piton-opts) "/"
                   gen-name)
         save-to (str "resources/" gen-path)]
    (io/make-parents save-to)
    (spit
     save-to
     "\n-- rollback \n")
    (make-piton-file)
    (println "Piton created " save-to)))

(defn piton
  "Piton migration management"
  [project subtask & args]
  (set-piton-opts project)
  (case subtask
    "new" (fresh-file
           (if (= "mig" (first args))
             :mig-path
             :seed-path)
           (second args))
    "migrate" (eval-in-project project
               `(piton.core/migrate
                 ~(:dburl @piton-opts)
                 ~(:dbuser @piton-opts)
                 ~(:dbpass @piton-opts)
                 ~@args)
               '(require 'piton.core))
    "seed" (eval-in-project project
               `(piton.core/seed
                 ~(:dburl @piton-opts)
                 ~(:dbuser @piton-opts)
                 ~(:dbpass @piton-opts)
                 ~@args)
               '(require 'piton.core))
    "rollback" (eval-in-project project
                `(piton.core/rollback
                  ~(:dburl @piton-opts)
                  ~(:dbuser @piton-opts)
                  ~(:dbpass @piton-opts)
                  ~(first args)
                  ~@(rest args))
                '(require 'piton.core))))
