(ns cljcastr.template-test
  (:require [babashka.fs :as fs]
            [cljcastr.template :as template]
            [cljcastr.test-utils :as test-utils]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each test-utils/test-dir-fixture)

(deftest has-template-var?-test
  (testing "happy path"
    (is (template/has-template-var? "{{foo}}"))
    (is (not (template/has-template-var? "foo")))
    (is (template/has-template-var? {:a ["foo" "bar"]
                                     :b {:c [1 2 3]}
                                     :d {:e "{{f}}"}}))
    (is (not (template/has-template-var? {:a ["foo" "bar"]
                                          :b {:c [1 2 3]}
                                          :d {:e "f"}})))))

(deftest expand-template-test
  (testing "arity 1"
    (is (= {:a "foobar", :b "foo"}
           (template/expand-template {:a "{{b}}bar", :b "foo"}))))

  (testing "arity 2"
    (is (= "foo")
        (template/expand-template "foo"
                                  {:foo "stuff", :bar "things"}))
    (is (= "stuff")
        (template/expand-template "{{foo}}"
                                  {:foo "stuff", :bar "things"}))

    (is (= ["foo" "bar"]
           (template/expand-template ["foo" "bar"]
                                     {:foo "stuff", :bar "things"})))

    (is (= ["stuff" "things"]
           (template/expand-template ["{{foo}}" "{{bar}}"]
                                     {:foo "stuff", :bar "things"})))

    (is (= {:a "foo", :b "bar"}
           (template/expand-template {:a "foo", :b "bar"}
                                     {:foo "stuff", :bar "things"})))

    (is (= {:a "stuff", :b "things"}
           (template/expand-template {:a "{{foo}}", :b "{{bar}}"}
                                     {:foo "stuff", :bar "things"})))

    (is (= {:a ["stuff" "two"]
            :b {:c "things", :d "e"}}
           (template/expand-template {:a ["{{foo}}" "two"]
                                      :b {:c "{{bar}}"
                                          :d "e"}}
                                     {:foo "stuff", :bar "things"}))))

  (testing "expand template from file"
    (test-utils/with-dirs [templates-dir]
      (fs/create-dirs templates-dir)
      (spit (fs/file templates-dir "foo.html")
            "Some {{foo}} and also {{bar}}.")
      (is (= {:content "Some stuff and also things."}
             (template/expand-template
              {:cljcastr.template/content "{{templates-dir}}/foo.html"}
              {:templates-dir templates-dir
               :foo "stuff"
               :bar "things"}))))))

(deftest expand-context-test

  (testing "arity 1"

    (is (= {:a "{{b}}"}
           (template/expand-context {:a "{{b}}"})))

    (is (= {:a "{{c}}", :b "{{c}}"}
           (template/expand-context {:a "{{b}}"
                                     :b "{{c}}"})))

    (is (= {:a "d", :b "d", :c "d"}
           (template/expand-context {:a "{{b}}"
                                     :b "{{c}}"
                                     :c "d"})))

    (is (= {:a ["d" "b",] :b "d", :c "d"}
           (template/expand-context {:a ["{{b}}" "b"]
                                     :b "{{c}}"
                                     :c "d"})))

    (is (= {:a "same", :b "same", :c ["same" "same" {:d "same"}]}
           (template/expand-context {:a "same"
                                     :b "{{a}}"
                                     :c ["same"
                                         "{{b}}"
                                         {:d "{{a}}"}]})))

    (testing "arity 2"
      (is (= {:a "same", :b "same", :c ["same" "{{a}}" {:d "same"}]}
             (template/expand-context 1 {:a "same"
                                         :b "{{a}}"
                                         :c ["same"
                                             "{{b}}"
                                             {:d "{{a}}"}]})))

      (is (= {:a "from ctx", :b "from ctx", :c "from opts"}
             (template/expand-context 5
                                      {:a "{{b}}"
                                       :b "from ctx"
                                       :c "{{d}}"}
                                      {:d "from opts"}))))

    (testing "arity 3"
      (is (= {:a "same"
              :b "same"
              :c ["same" "{{a}}" {:d "same"}]
              :e "from opts"
              :f "{{g}}"}
             (template/expand-context 1
                                      {:a "same"
                                       :b "{{a}}"
                                       :c ["same"
                                           "{{b}}"
                                           {:d "{{a}}"}]
                                       :e "{{g}}"
                                       :f "{{h}}"}
                                      {:g "from opts"
                                       :h "{{g}}"})))

      (is (= {:a "from ctx", :b "from ctx", :c "from opts", :d "deep from opts"}
             (template/expand-context 5
                                      {:a "{{b}}"
                                       :b "from ctx"
                                       :c "{{e}}"
                                       :d "{{f}}"}
                                      {:e "from opts"
                                       :f "deep {{e}}"}))))))
