(ns cljcastr.dom
  (:require [clojure.string :as str]))

(defn error! [& msgs]
  (throw (js/Error. (str/join " " msgs))))

(defn get-el
  "Returns the first element in the document matching the XPath selector
   `selector`. If the `el` param is present, the selector applies to its
   children instead of the top-level document. If `selector` is not a string,
   it is assumed to be an element and just returned."
  ([selector]
   (get-el js/document selector))
  ([el selector]
   (if (string? selector)
     (when selector
       (.querySelector el selector))
     selector)))

(defn get-els
  "Like `get-el`, but returns all elements matching `selector`. If `selector` is
   not a string, it is assumed to be a list of elements and just returned."
  ([selector]
   (get-els js/document selector))
  ([el selector]
   (if (string? selector)
     (when selector
       (.querySelectorAll el selector))
     ;; Selector is not a string, so assume it's an element and return it
     selector)))

(defn get-selector
  "Returns an XPath selector for element `el` using its ID, or `nil` if it has no
   ID. `el` may also be a string, in which case it is assumed to be a selector
   and just returned."
  [el]
  (when el
    (let [selector (if (string? el) el (str "#" (.-id el)))]
      (when (not= "#" selector)
        selector))))

(defn get-html
  "Returns inner HTML of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (.-innerHTML el)))

(defn get-text
  "Returns inner text of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (or (.-textContent el) (.-innerText el))))

(defn get-value
  "Returns value of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (.-value el)))

(defn get-child
  "Returns the `n`th child of the element identified by `selector`. `selector`
   may also be an element. If `n` is out of bounds, returns `nil`."
  [selector n]
  (when-let [el (get-el selector)]
    (->> el .-childNodes seq (drop n) first)))

(defn get-children
  "Returns children of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (-> el .-childNodes seq)))

(defn add-children!
  "Adds list of `children` to the element identified by `selector`. `selector`
   may also be an element."
  [selector children]
  (when-let [el (get-el selector)]
    (doseq [child children]
      (.appendChild el child))))

(defn add-child!
  "Convenience function to add a single child to the element identified by
   `selector`. `selector` may also be an element."
  [selector child]
  (add-children! selector [child]))

(defn insert-child-after!
  "Inserts `child` after the element identified by `selector`. `selector` may
   also be an element."
  [selector child]
  (when-let [sibling (get-el selector)]
    (let [next-sibling (.-nextSibling sibling)
          parent (.-parentNode sibling)]
      (if next-sibling
        (.insertBefore parent child next-sibling)
        (add-child! parent child)))))

(defn insert-child-before!
  "Inserts `child` before the element identified by `selector`. `selector` may
   also be an element."
  [selector child]
  (when-let [sibling (get-el selector)]
    (.insertBefore (.-parentNode sibling) child sibling)))

(defn insert-child!
  "Inserts `child` as the nth child of the element identified by `selector`.
   `selector` may also be an element. If the element has no children or `n` is
   at the end of the list (or beyond), `child` will be added with `add-child!`."
  [selector child n]
  (when-let [el (get-el selector)]
    (let [n (if (neg? n) 0 n)
          children (seq (.-childNodes el))
          num-children (count children)]
      (if (or (empty? children) (>= n (dec num-children)))
        (add-child! el child)
        (.insertBefore el child (nth children n))))))

(defn clear-children!
  "Removes all children from the element identified by `selector`. `selector`
   may also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (.replaceChildren el)))

(defn set-children!
  "Sets children of the element identified by `selector` to the list of
   `children`. `selector` may also be an element."
  [selector children]
  (when-let [el (get-el selector)]
    (clear-children! el)
    (add-children! el children)))

(defn set-child!
  "Convenience function to set children of the element identified by
   `selector` to a single `child`. `selector` may also be an element."
  [selector child]
  (set-children! selector [child]))

(defn take-children!
  "Sets children of the element identified by `selector` to its first `n`
   children. `selector` may also be an element."
  [selector n]
  (when-let [el (get-el selector)]
    (->> el .-childNodes seq (take n) (set-children! el))))

(defn remove-child!
  "Removes `child` from its parent."
  [child]
  (when-let [parent (.-parentNode child)]
    (.removeChild parent child)))

(defn add-classes!
  "Adds `classes` to the element identified by `selector`. `selector` may also be
   an element."
  [selector classes]
  (when-let [el (get-el selector)]
    (let [classes (if (sequential? classes) classes [classes])]
      (doseq [cls classes]
        (-> el .-classList (.add cls))))))

(defn add-class!
  "Convenience function to add a single class `cls` to the element identified by
   `selector`. `selector` may also be an element."
  [selector cls]
  (add-classes! selector [cls]))

(defn clear-classes!
  "Removes all classes from the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (when-let [el (get-el selector)]
    (set! (.-className el) "")))

(defn set-classes!
  "Sets classes of the element identified by `selector` to list of `classes`.
   `selector` may also be an element."
  [selector classes]
  (when-let [el (get-el selector)]
    (clear-classes! el)
    (add-classes! el classes)))

(defn set-class!
  "Convenience function to set classes of the element identified by `selector`
   to a single class `cls`. `selector` may also be an element."
  [selector cls]
  (set-classes! selector [cls]))

(defn remove-class!
  "Removes class `cls` from the element identified by `selector`. `selector` may
   also be an element."
  [selector cls]
  (when-let [el (get-el selector)]
    (-> el .-classList (.remove cls))))

(defn has-class?
  "Returns true if the element identified by `selector` has class `cls`.
   `selector` may also be an element."
  [selector cls]
  (when-let [el (get-el selector)]
    (-> el .-classList (.contains cls))))

(defn set-attribute!
  "Sets attribute `attr` of the element identified by `selector` to `v`.
   `selector` may also be an element."
  [selector attr v]
  (when-let [el (get-el selector)]
    (.setAttribute el (name attr) v)))

(defn set-attributes!
  "Given a list of attribute name / value pairs `attrs`, sets each attribute of
   the element identified by `selector` to the corresponding value. `selector`
   may also be an element."
  [selector attrs]
  (when-let [el (get-el selector)]
    (if (or (sequential? attrs) (map? attrs))
      (doseq [[k v] attrs]
        (set-attribute! el k v))
      (error! "attrs must be a list of attribute / value pairs; was:"
              (type attrs)))))

(defn set-html!
  "Sets inner HTML of the element identified by `selector` to `html`.
   `selector` may also be an element."
  [selector html]
  (when-let [el (get-el selector)]
    (set! (.-innerHTML el) html)))

(defn set-styles!
  "Sets styles of the element identified by `selector` to `styles`. `selector`
   may also be an element."
  [selector styles]
  (set! (.-style (get-el selector)) styles))

(defn set-text!
  "Sets inner text of the element identified by `selector` to `text`. `selector`
   may also be an element."
  [selector text]
  (when-let [el (get-el selector)]
    (set! (.-textContent el) text)))

(defn set-value!
  "Sets value of the element identified by `selector` to `v`. `selector` may
   also be an element."
  [selector v]
  (when-let [el (get-el selector)]
    (set! (.-value el) v)))

(defn select-el!
  "Selects all text in the element identified by `selector`. `selector` may also
   be an element."
  [selector]
  (when-let [el (get-el selector)]
    (let [sel (js/window.getSelection)
          rng (js/document.createRange)]
      (.selectNodeContents rng el)
      (.removeAllRanges sel)
      (.addRange sel rng))))

(defn create-el
  "Creates an element of type `el-type`. If the `:id` key is present in `opts`,
   the id of the element will be set to it. If the `:class` key is present in
   `opts`, that class will be added to it."
  ([el-type]
   (create-el el-type {}))
  ([el-type opts]
   (let [el (js/document.createElement el-type)]
     (when (:id opts) (set! (.-id el) (:id opts)))
     (when (:class opts) (add-class! el (:class opts)))
     (when (:classes opts) (add-classes! el (:classes opts)))
     (when (:text opts) (set-text! el (:text opts)))
     el)))

(defn move-cursor!
  "Moves the cursor to the specified offset within the editable element
   identified by `selector`. `selector` may also be an element. If `offset`
   is `:end`, the cursor will be placed at the end, and if it is negative, the
   cursor will be placed `offset` characters from the end."
  [selector offset]
  (when-let [el (get-el selector)]
    (let [el (-> el (get-child 0))  ; the child is a Text object
          len (count (get-text el))
          sel (js/window.getSelection)
          rng (js/document.createRange)]
      (let [offset (cond
                     (= :end offset) len
                     (neg? offset) (+ len offset)
                     :else offset)]
        (.setStart rng el offset)
        (.setEnd rng el offset))
      (.removeAllRanges sel)
      (.addRange sel rng))))

(defn add-listener!
  "Adds an event listener to the element specified by `selector` for events of
   type `event-type`, registering in the `state` atom. `selector` may also be an
   element, in which case the listener is registered in `state` by the ID of the
   element, unless it is `js/document` or `js/winodw`, which are handled as
   special cases. If the element has no ID and is not `js/document` or
   `js/window`, the listener is not registered and therefore cannot be removed by
   `clear-listeners!`."
  [state selector event-type f]
  (.addEventListener (get-el selector) event-type f)
  (let [selector (or (get-selector selector)
                     (when (= js/document selector) "js/document")
                     (when (= js/window selector) "js/window"))]
    (when selector
      (swap! state update-in [:listeners selector event-type] #(cons f %))
      (get-in @state [:listeners selector]))))

(defn clear-listeners!
  "Removes all listeners from the element specified by `selector` and updates the
   `state` atom. If `event-type` is passed, only listeners for the specified
   event type are removed. If only the `state` atom is passed, removes all
   listeners on all elements. `selector` may also be an element with an ID or
   `js/document`; otherwise, nothing will be done."
  ([state]
   (doseq [[selector _] (:listeners @state)]
     (clear-listeners! state selector)))
  ([state selector]
   (let [selector (get-selector selector)]  ; handle `selector` being an element
     (when selector
       (doseq [[event-type _] (get-in @state [:listeners selector])]
         (clear-listeners! state selector event-type)))))
  ([state selector event-type]
   (when-let [selector (get-selector selector)]
     (let [el (cond
                (= "js/document" selector) js/document
                (= "js/window" selector) js/window
                :else (get-el selector))]
       (doseq [f (get-in @state [:listeners selector event-type])]
         (.removeEventListener el event-type f)))
     (swap! state update-in [:listeners selector] dissoc event-type)
     (get-in @state [:listeners selector]))))
