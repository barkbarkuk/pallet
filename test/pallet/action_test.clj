(ns pallet.action-test
  (:use
   clojure.test
   pallet.action
   pallet.action-impl
   [pallet.action-plan :only [translate]]
   [pallet.monad :only [let-s]]
   [pallet.node-value :only [node-value?]]
   [pallet.session.action-plan :only [get-action-plan]]
   [pallet.test-utils :only [test-session]]))

(deftest declare-action-test
  (testing "default execution"
    (let [inserter (declare-action 'a0 {:always-before :a})
          action (-> inserter meta :action)]
      (is (fn? inserter))
      (testing "action"
        (is (map? action))
        (is (= 'a0 (action-symbol action)))
        (is (= :in-sequence (action-execution action)))
        (is (= {:always-before :a} (action-precedence action))))
      (testing "inserter"
        (let [[nv session] ((inserter 1) {})
              action-plan (get-action-plan session)
              action-map (ffirst action-plan)]
          (is (node-value? nv))
          (is (seq action-plan))
          (is (map? action-map))
          (is (nil? (:pallet.action/action-plan session)))
          (is (= action (:action action-map)))
          (is (= [1] (:args action-map)))))))
  (testing "explicit execution"
    (let [inserter (declare-action 'a0 {:execution :aggregated})]
      (is (= :aggregated (action-execution (-> inserter meta :action)))))))

(defaction a1 {:always-before :a} [arg])
(deftest defaction-test
  (let [action (-> a1 meta :action)]
    (testing "action"
      (is (map? action))
      (is (= `a1 (action-symbol action)))
      (is (= {:always-before :a} (action-precedence action)))
      (is (= :in-sequence (action-execution action))))
    (testing "inserter"
      (is (fn? a1))
      (let [[nv session] ((a1 1) {})
            action-plan (get-action-plan session)
            action-map (ffirst action-plan)]
        (is (node-value? nv))
        (is (seq action-plan))
        (is (map? action-map))
        (is (= action (:action action-map)))
        (is (= [1] (:args action-map))))))
  (testing "explicit execution"
    (defaction a2 {:execution :aggregated} [arg])
    (let [action (-> a2 meta :action)]
      (testing "action"
        (is (map? action))
        (is (= `a2 (action-symbol action)))
        (is (= {} (action-precedence action)))
        (is (= :aggregated (action-execution action)))))))

(defaction iat-action [arg])
(deftest implement-action-test
  (testing "implement-action*"
    (let [f (fn [session arg] [arg session])]
      (implement-action* iat-action :x {:y 1} f)
      (is (= f (action-fn iat-action :x)))))
  (testing "implement-action"
    (implement-action iat-action :a {:b 1} [session arg] [arg session])
    (is (fn? (action-fn iat-action :a)))))

(deftest declare-aggregated-crate-action-test
  (let [f (fn [session] [nil session])
        inserter (declare-aggregated-crate-action 'a f)
        action (-> inserter meta :action)]
    (is (= :aggregated-crate-fn (action-execution action)))
    (is (= f (:f (action-implementation action :default))))
    (is (= f (action-fn inserter :default)))))

(deftest declare-delayed-crate-action-test
  (let [f (fn [session] [nil session])
        inserter (declare-delayed-crate-action 'a f)
        action (-> inserter meta :action)]
    (is (= :delayed-crate-fn (action-execution action)))
    (is (= f (:f (action-implementation action :default))))
    (is (= f (action-fn inserter :default)))))

(deftest with-action-options-test
  (testing "precedence across execution model"
    (let [agg (declare-action 'agg {:execution :aggregated})
          ins (declare-action 'ins {})
          session (test-session {})
          p (let-s [_ (agg "hello")
                    _ (with-action-options {:always-before #{agg}}
                        (ins "a"))]
                   nil)
          [_ session] (p session)
          action-plan (get-action-plan session)
          [a1 a2] (#'pallet.action-plan/pop-block action-plan)]
      (is (= 'agg (action-symbol (:action a1))))
      (is (= 'ins (action-symbol (:action a2))))
      (let [[action-plan _] (translate action-plan session)
            [a1 a2] action-plan]
        (is (= 'ins (action-symbol (:action a1))))
        (is (= 'agg (action-symbol (:action a2))))))))
