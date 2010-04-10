(ns pallet.crate.tomcat-test
  (:use [pallet.crate.tomcat] :reload-all)
  (:require [pallet.template :only [apply-templates]])
  (:use clojure.test
        pallet.test-utils
        [pallet.resource.package :only [package package-manager]]
        [pallet.resource :only [build-resources]]
        [pallet.stevedore :only [script]]
        [pallet.utils :only [cmd-join]]
        [clojure.contrib.java-utils :only [file]]))

(deftest tomcat-test
  (is (= "debconf-set-selections <<EOF\ndebconf debconf/frontend select noninteractive\ndebconf debconf/frontend seen false\nEOF\naptitude install -y  tomcat6\n"
         (build-resources [] (tomcat)))))

(deftest tomcat-deploy-test
  (is (= "cp file.war /var/lib/tomcat6/webapps/\n/etc/init.d/tomcat6 stop\n/etc/init.d/tomcat6 start\n"
         (build-resources [] (tomcat-deploy "file.war")))))

(deftest tomcat-policy-test
  (is (= "cat > /etc/tomcat6/policy.d/100hudson.policy <<'EOF'\ngrant codeBase \"file:${catalina.base}/webapps/hudson/-\" {\npermission java.lang.RuntimePermission \"getAttribute\";\n};\nEOF\n"
         (build-resources []
          (tomcat-policy
           100 "hudson"
           {"file:${catalina.base}/webapps/hudson/-"
            ["permission java.lang.RuntimePermission \"getAttribute\""]})))))

(deftest tomcat-application-conf-test
  (is (= "cat > /etc/tomcat6/Catalina/localhost/hudson.xml <<'EOF'\n<?xml version='1.0' encoding='utf-8'?>\n<Context docBase=\"/srv/hudson/hudson.war\">\n<Environment name=\"HUDSON_HOME\"/>\n</Context>\nEOF\n"
         (build-resources []
          (tomcat-application-conf
           "hudson"
           "<?xml version='1.0' encoding='utf-8'?>
<Context docBase=\"/srv/hudson/hudson.war\">
<Environment name=\"HUDSON_HOME\"/>
</Context>")))))

(deftest tomcat-user-test
  (is (= "<decl version=\"1.1\"/><tomcat-users><role rolename=\"r1\"/><role rolename=\"r2\"/><user username=\"u2\" password=\"p2\" roles=\"r1,r2\"/><user username=\"u1\" password=\"p1\" roles=\"r1\"/></tomcat-users>\n"
         (build-resources []
          (tomcat-user :role "r1" "u1" {:password "p1" :roles ["r1"]})
          (tomcat-user :role "r2" "u2" {:password "p2" :roles ["r1","r2"]})))))