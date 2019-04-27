(ns phlegyas-example.core
  (:require [phlegyas.core :as pcore]
            [phlegyas.vfs :as vfs]
            [phlegyas.state :as pstate]
            [phlegyas.frames :refer :all]
            [phlegyas.util :refer :all]
            [clojure.java.shell :as shell]
            [aleph.tcp :as tcp]
            [manifold.stream :as s])
  (:gen-class))

;; with-frame-bindings is a macro that may be useful for writing your functions.
;; it will create a lexical environment pre-populated with useful variables that
;; you will likely use when writing your logic. see the `phlegyas.util` namespace
;; for a full definition of the variables.
;;
;; for read/write, what is most likely useful is `frame-ftype`, which will be
;; the type of frame that we were called with. for write operations, this will
;; be `:Twrite`, and for read operations, `:Tread`. Also useful is the `frame-offset`
;; and `frame-count` variables, indicating the read offset and requested byte count.
;; also, `frame-data`, which will include any data sent during a write call.
;;
;; functions called on files have arity 3, taking in the stat structure for itself,
;; the frame data that it was invoked with, and the global state atom.
;;
;; these three should be plenty to mutate your environment and respond ad required.
;; note however the default vfs is not yet fully fleshed out. certain operations,
;; such as `:Tcreate`, `:Twstat`, `:Tremove`, etc., are not implemented. however,
;; you can replace the entire state machine (or only portions thereof) as you see
;; fit, and or rip out the entire VFS / state machine layer. i've tried to make it
;; somewhat sane.

(defn another-file-function
  "This will execute a shell command and return the bytes."
  [stat frame state]
  (with-frame-bindings frame
    (do
      (if (> frame-offset 0)
        (byte-array 0)
        (.getBytes (:out (shell/sh "uname" "-a")))))))

(defn file-function
  "Simply print 'hello, world!\n' if reading from the beginning of the file, or
  else return nothing."
  [stat frame state]
  (with-frame-bindings frame
    (do
      (if (> frame-offset 0)
        (byte-array 0)
        (.getBytes "hello, world!\n" "UTF-8")))))

(defn my-filesystem
  "An example filesystem constructor. `vfs/create-filesystem` creates the root directory,
  to which all additional files or directories must be added. `vfs/create-synthetic-file`
  takes as a first argument the file name, and the second argument the function that is
  called on read/write calls. `vfs/insert-file!` adds a file to a directory structure."
  []
  (let [fs (vfs/create-filesystem)
        root-path (:root-path fs)
        my-file (vfs/create-synthetic-file "hello-world" #'file-function)
        another-file (vfs/create-synthetic-file "hello-world-2" #'another-file-function)]
    (-> fs
        (vfs/insert-file! root-path my-file)
        (vfs/insert-file! root-path another-file))))

(defn start-server
  "This creates a file server, taking in an inport and an outport, and a filesystem
  constructor function. `state` is an atomic map that is updated depending on incoming
  messages, and can be read and modified from within file function calls.

  `pstate/state-handler` is a provided state machine that will handle most calls and
  update the state for us, and assemble replies to inject into `outgoing-frame-stream`,
  which is connected via `assemble-packet` for correct data encoding before going out
  over the wire."
  [in out root-filesystem-constructor]
  (let [state (atom {:root-filesystem root-filesystem-constructor})
        incoming-frame-stream (s/stream)
        outgoing-frame-stream (s/stream)
        _ (frame-assembler in incoming-frame-stream)]
    (s/connect-via outgoing-frame-stream #(s/put! out (assemble-packet %)) out)
    (pstate/consume-with-state incoming-frame-stream outgoing-frame-stream state #'pstate/state-handler)))

(defn entrypoint
  "Application entrypoint. Called on every established TCP connection. Starts a 9P server
  instance, with the `my-filesystem` function called for the root filesystem."
  [socket info]
  (let [in (s/stream)
        out (s/stream)
        _ (pcore/server! in out :root-filesystem-constructor my-filesystem)]
    (s/connect socket in)
    (s/connect out socket)))

(defn -main
  "Start a server, listening on port 10001."
  [& args]

  (tcp/start-server entrypoint {:port (parse-int (first args)) :join? true}))
