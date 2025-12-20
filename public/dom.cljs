(ns cljcastr.dom)

(defn get-el
  "Returns the first element in the document matching the XPath selector
   `selector`. If the `el` param is present, the selector applies to its
   children instead of the top-level document. If `selector` is not a string,
   it is assumed to be an element and just returned."
  ([selector]
   (get-el js/document selector))
  ([el selector]
   (if (string? selector)
     (.querySelector el selector)
     selector)))

(defn get-els
  "Like `get-el`, but returns all elements matching `selector`. If `selector` is
   not a string, it is assumed to be a list of elements and just returned."
  ([selector]
   (get-els js/document selector))
  ([el selector]
   (if (string? selector)
     (.querySelectorAll el selector)
     ;; Selector is not a string, so assume it's an element and return it
     selector)))

(defn get-selector
  "Returns an XPath selector for element `el` using its ID, or `nil` if it has no
   ID. `el` may also be a string, in which case it is assumed to be a selector
   and just returned."
  [el]
  (let [selector (if (string? el) el (str "#" (.-id el)))]
    (when (not= "#" selector)
      selector)))

(defn get-html
  "Returns inner HTML of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (.-innerHTML (get-el selector)))

(defn get-text
  "Returns inner text of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (let [el (get-el selector)]
    (or (.-textContent el) (.-innerText el))))

(defn get-value
  "Returns value of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (.-value (get-el selector)))

(defn create-el
  "Creates an element of type `el-type`. If the `:id` key is present in `opts`,
   the id of the element will be set to it. If the `:class` key is present in
   `opts`, that class will be added to it."
  ([el-type]
   (create-el el-type {}))
  ([el-type opts]
   (let [el (js/document.createElement el-type)]
     (when (:id opts) (set! (.-id el) (:id opts)))
     (when (:class opts) (-> el .-classList (.add (:class opts))))
     el)))

(defn get-children
  "Returns children of the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (-> (get-el selector) .-childNodes seq))

(defn add-children!
  "Adds list of `children` to the element identified by `selector`. `selector`
   may also be an element."
  [selector children]
  (let [el (get-el selector)]
    (doseq [child children]
      (.appendChild el child))))

(defn add-child!
  "Convenience function to add a single child to the element identified by
   `selector`. `selector` may also be an element."
  [selector child]
  (add-children! selector [child]))

(defn clear-children!
  "Removes all children from the element identified by `selector`. `selector`
   may also be an element."
  [selector]
  (.replaceChildren (get-el selector)))

(defn set-children!
  "Sets children of the element identified by `selector` to the list of
   `children`. `selector` may also be an element."
  [selector children]
  (let [el (get-el selector)]
    (clear-children! el)
    (add-children! el children)))

(defn set-child!
  "Convenience function to set children of the element identified by
   `selector` to a single `child`. `selector` may also be an element."
  [selector child]
  (set-children! selector [child]))

(defn add-classes!
  "Adds `classes` to the element identified by `selector`. `selector` may also be
   an element."
  [selector classes]
  (let [el (get-el selector)]
    (doseq [cls classes]
      (-> el .-classList (.add cls)))))

(defn add-class!
  "Convenience function to add a single class `cls` to the element identified by
   `selector`. `selector` may also be an element."
  [selector cls]
  (add-classes! selector [cls]))

(defn clear-classes!
  "Removes all classes from the element identified by `selector`. `selector` may
   also be an element."
  [selector]
  (set! (.-className (get-el selector)) ""))

(defn set-classes!
  "Sets classes of the element identified by `selector` to list of `classes`.
   `selector` may also be an element."
  [selector classes]
  (let [el (get-el selector)]
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
  (-> (get-el selector) .-classList (.remove cls)))

(defn has-class?
  "Returns true if the element identified by `selector` has class `cls`.
   `selector` may also be an element."
  [selector cls]
  (-> (get-el selector) .-classList (.contains cls)))

(defn set-attribute!
  "Sets attribute `attr` of the element identified by `selector` to `v`.
   `selector` may also be an element."
  [selector attr v]
  (.setAttribute (get-el selector) attr v))

(defn set-html!
  "Sets inner HTML of the element identified by `selector` to `html`.
   `selector` may also be an element."
  [selector html]
  (set! (.-innerHTML (get-el selector)) html))

(defn set-styles!
  "Sets styles of the element identified by `selector` to `styles`. `selector`
   may also be an element."
  [selector styles]
  (set! (.-style (get-el selector)) styles))

(defn set-text!
  "Sets inner text of the element identified by `selector` to `text`. `selector`
   may also be an element."
  [selector text]
  (set! (.-textContent (get-el selector)) text))

(defn set-value!
  "Sets value of the element identified by `selector` to `v`. `selector` may
   also be an element."
  [selector v]
  (set! (.-value (get-el selector)) v))

(defn select-el!
  "Selects all text in the element identified by `selector`. `selector` may also
   be an element."
  [selector]
  (let [el (get-el selector)
        sel (js/window.getSelection)
        rng (js/document.createRange)]
    (.selectNodeContents rng el)
    (.removeAllRanges sel)
    (.addRange sel rng)))

(defn add-listener!
  "Adds an event listener to the element specified by `selector` for events of
   type `event-type`, registering in the `state` atom. `selector` may also be an
   element, in which case the listener is registered in `state` by the ID of the
   element, unless it is `js/document`, which is handled as a special case. If
   the element has no ID and is not `js/document`, the listener is not registered
   and therefore cannot be removed by `clear-listeners!`."
  [state selector event-type f]
  (.addEventListener (get-el selector) event-type f)
  (let [selector (or (get-selector selector)
                     (when (= js/document selector) "js/document"))]
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
     (let [el (if (= "js/document" selector) js/document (get-el selector))]
       (doseq [f (get-in @state [:listeners selector event-type])]
         (.removeEventListener el event-type f)))
     (swap! state update-in [:listeners selector] dissoc event-type)
     (get-in @state [:listeners selector]))))
