#!/bin/bash
if [[ $(lein with-profile travis pprint :version) =~ .*\-SNAPSHOT ]]; then
    lein deploy clojars
fi
