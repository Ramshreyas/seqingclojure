(ns clojure-parser.ast-converter
  (:require [clojure-parser.core :as ast-gen]
            [clojure.tools.trace :as trace]
            [clojure.pprint :as pprint]
            [clojure-parser.utilities :refer :all])
  (:gen-class))

(comment TODO
         write jsonify
         send the json to esgenerate and get js code
         write basic test cases so that regression testing is easy
         macro support - check ast generator if it expands the macro(built-in and domonad))

(comment BUGS
         nested maps and arrays dont work - generator is broken)

(declare get-form get-return-form get-forms)

(defn jsonify [json-map]
  ; replace nil with null
  ; replace = with ===
  )

(defn get-identifier [identifier]
   {"type" "Identifier"
    "name" (str identifier)})

(defn get-block [body]
  {"type" "BlockStatement"
   "body" body})

(defn get-do-common [form func]
  (let [body (operands form)]
    (get-block (conj (get-forms (butlast body) :do)
                     (func (last body) :do)))))

(defn get-do [form]
  (get-do-common form get-form))

(defn get-return-do [form]
  (get-do-common form get-return-form))

(defn get-literal-raw-value [value type]
  (condp = type
    'number (str value)
    'string (quotify value)
    'boolean (str value)
    'nil "null"))

(defn get-literal
  ([value]
   (cond
    (number? value) (get-literal value 'number)
    (string? value) (get-literal value 'string)
    (instance? Boolean value) (get-literal value 'boolean)
    (= :nil value) (get-literal nil 'nil)
    true (get-identifier value)))

  ([value type]
   (assoc {"type" "Literal" "value" value} "raw" (get-literal-raw-value value type))))

(defn get-operator [form]
  (let [operator (operator form)
        operands (operands form)
        jst {"type" "BinaryExpression"
             "operator" (str operator)
             "right" (get-form (last operands) :op)}]
    (if (= (count operands) 2)
      (assoc jst "left" (get-form (first operands) :op))
      (assoc jst "left" (get-operator {operator (butlast operands)})))))

(defn get-return [argument]
  {"type" "ReturnStatement"
   "argument" argument})


(defn get-if-common [form func]
  (let [body (operands form)]
    {"type" "IfStatement"
     "test" (get-form (first body) :if-test)
     "consequent" (func (second body) :if)
     "alternate" (func (last body) :if)}))

(defn get-if [form]
  (get-if-common form get-form))

(defn get-return-if [form]
  (get-if-common form get-return-form))

(defn get-return-form [form parent]
  (cond (if? form) (get-return-if form)
        (do? form) (get-return-do form)
        :else (get-return (get-form form :return))))

(defn get-fn-body [forms]
  (get-block (conj (get-forms (butlast forms) :defn)
                   (get-return-form (last forms) :defn))))

(def get-fn-param get-identifier)

(defn get-fn-params [params]
  ; TODO generalize
  (into [] (map get-fn-param params)))

(defn get-fn [form]
  (let [operands (operands form)
        identifier (first operands)
        value (second operands)]
    {"type" "FunctionDeclaration"
     "id" (get-identifier identifier)
     "params" (get-fn-params (:vector (first (:fn value))))
     "body" (get-fn-body (rest (:fn value)))
     "generator" false
     "expression" false}))

(defn get-var [form]
  (let [operands (operands form)
        identifier (first operands)
        value (second operands)]
    {"type" "VariableDeclaration"
     "declarations" [{"type" "VariableDeclarator"
                      "id" (get-identifier identifier)
                      "init" (get-form value :var)}]
     "kind" "var"}))

(defn get-def [form]
  (if (defn? form)
    (get-fn form)
    (get-var form)))

(defn get-fn-call [form]
  {"type" "CallExpression"
   "callee" (get-identifier (operator form))
   "arguments" (get-forms (operands form) :fn-call)})

(defn get-exp [form]
  {"type" "ExpressionStatement"
   "expression" (get-form form :exp)})

(defn get-map-property [property]
  {"type" "Property"
   "key" (get-form (first property) :map)
   "computed" false
   "value" (get-form (second property) :map)
   "kind" "init"
   "method" false
   "shorthand" false})

(defn get-map-properties [properties]
  (into [] (map get-map-property (partition 2 properties))))

(defn get-map-ds [form]
  {"type" "ObjectExpression"
   "properties" (get-map-properties (operands form))})

(defn get-vec [form]
  {"type" "ArrayExpression"
   "elements" (get-forms (operands form) :vec)})

(defn get-form [form parent]
  (cond
   (and (form-is? form [vec? literal? operator? fn-call?]) (contains? #{:defn :if :do :program} parent)) (get-exp form)
   (def? form) (get-def form)
   (if? form) (get-if form)
   (do? form) (get-do form)
   (vec? form) (get-vec form)
   (map-ds? form) (get-map-ds form)
   (literal? form) (get-literal form)
   (operator? form) (get-operator form)
   (fn-call? form) (get-fn-call form)
   :else {:not-a-form form}))

(defn get-forms [forms parent]
  (into [] (map #(get-form % parent) forms)))

(defn get-program [body]
  {"type" "Program"
   "body" body
   "sourceType" "script"})

(defn get-ast [ast]
  (get-program (get-forms (:program ast) :program)))

(defn -main []
  ; (trace/trace-ns 'clojure-parser.ast-converter)
  ; (trace/trace-ns 'clojure-parser.utilities)
  ; (trace/trace-vars )
  (def ast (ast-gen/-main "test.clj"))
  (pprint/pprint ast)
  (println "\n\n\n\n")
  (print-json (get-ast ast)))

(-main)
