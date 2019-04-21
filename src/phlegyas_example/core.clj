(ns phlegyas-example.core
  (:require [phlegyas.core :as pcore]
            [phlegyas.vfs :as vfs]
            [phlegyas.util :refer :all]
            [clojure.java.shell :as shell]
            [aleph.tcp :as tcp]
            [manifold.stream :as s])
  (:gen-class))

(defn another-file-function
  [stat frame state]
  (with-frame-bindings frame
    (do
      (if (> frame-offset 0)
        (byte-array 0)
        (.getBytes (:out (shell/sh "uname" "-a")))))))

(defn file-function
  [stat frame state]
  (with-frame-bindings frame
    (do
      (if (> frame-offset 0)
        (byte-array 0)
        (.getBytes "hello, world!\n" "UTF-8")))))

(defn my-filesystem
  []
  (let [fs (vfs/create-filesystem)
        root-path (:root-path fs)
        my-file (vfs/create-synthetic-file "hello-world" #'file-function)
        another-file (vfs/create-synthetic-file "hello-world-2" #'another-file-function)]
    (-> fs
        (vfs/insert-file! root-path my-file)
        (vfs/insert-file! root-path another-file))))

(defn entrypoint
  [socket info]
  (let [in (s/stream)
        out (s/stream)
        _ (pcore/server! in out :root-filesystem-constructor my-filesystem)]
    (s/connect socket in)
    (s/connect out socket)))

(defn -main
  [& args]
  (tcp/start-server entrypoint {:port 10001 :join? true}))
