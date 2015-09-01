;; These profiles should be used as profiles.clj on Travis CI.
{:auth {:repository-auth {#"https://clojars.org/repo"
                          {:username :env, :password :env}}}
 :travis {:plugins [[lein-pprint "1.1.1"]]
          :android {:sdk-path "/usr/local/android-sdk/"}
          :deploy-repositories [["releases" :clojars]]}
 :android-user {:dependencies [[cider/cider-nrepl "0.9.0-SNAPSHOT"]]
                :android {:aot-exclude-ns ["cider.nrepl.middleware.util.java.parser"
                                           "cider.nrepl" "cider-nrepl.plugin"]}}}

