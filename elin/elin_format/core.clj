(ns elin-format.core
  (:require
   [babashka.process :as proc]
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.util.file :as e.u.file]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(defn- format-by-command
  [commands dir code]
  (try
    (->> commands
         (apply proc/process {:dir dir :in code :out :string})
         (:out)
         (deref))
    (catch Exception ex
      (e/fault {:message (ex-message ex)}))))

(defn- get-root-dir
  [host]
  (e/let [cwd (async/<!! (e.p.host/get-current-working-directory! host))]
    (or (e.u.file/get-project-root-directory cwd)
        ".")))

(def format-current-form-interceptor
  (let [cached-root-dir (atom nil)]
    {:kind e.c.interceptor/autocmd
     :enter (-> (fn [{:as ctx :component/keys [host]}]
                  (e/let [{:keys [command]} (e.u.interceptor/config ctx #'format-current-form-interceptor)
                          _ (when (empty? command)
                              (e/fault {:message "No command is configured"}))
                          root-dir (or @cached-root-dir
                                       (let [root-dir' (get-root-dir host)]
                                         (reset! cached-root-dir root-dir')
                                         root-dir'))
                          {cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
                          {:keys [code lnum col]} (e.f.sexpr/get-top-list ctx cur-lnum cur-col)
                          formatted-code (format-by-command command root-dir code)]
                    (e.f.sexpr/replace-list-sexpr ctx lnum col formatted-code)))
                (ix/when #(and (= "BufWritePre" (:autocmd-type %))
                               (e.u.interceptor/connected? %)))
                (ix/discard))}))
