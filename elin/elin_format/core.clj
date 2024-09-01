(ns elin-format.core
  (:require
    [babashka.process :as proc]
    [clojure.core.async :as async]
    [elin.constant.interceptor :as e.c.interceptor]
    [elin.error :as e]
    [elin.function.sexpr :as e.f.sexpr]
    [elin.protocol.host :as e.p.host]
    [elin.util.interceptor :as e.u.interceptor]
    [exoscale.interceptor :as ix]))

(defn- format-by-command
  [commands code]
  (try
    (->> commands
         (apply proc/process {:in code :out :string})
         (:out)
         (deref))
    (catch Exception ex
      (e/fault {:message (ex-message ex)}))))

(def format-current-form-interceptor
  {:name ::format-current-form-interceptor
   :kind e.c.interceptor/autocmd
   :params ["cat"]
   :enter (-> (fn [{:as ctx :component/keys [host]}]
                (e/let [{:keys [params]} (e.u.interceptor/self ctx)
                        {cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
                        {:keys [code lnum col]} (e.f.sexpr/get-top-list ctx cur-lnum cur-col)
                        formatted-code (format-by-command params code)]
                  (e.f.sexpr/replace-list-sexpr ctx lnum col formatted-code)))
              (ix/when #(and (= "BufWritePre" (:autocmd-type %))
                             (e.u.interceptor/connected? %)))
              (ix/discard))})

