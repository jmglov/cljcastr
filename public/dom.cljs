(ns cljcastr.dom)

(defn add-class! [el cls]
  (-> el (.-classList) (.add cls)))

(defn get-el [selector]
  (if (string? selector)
    (js/document.querySelector selector)
    ;; Selector is not a string, so assume it's an element and return it
    selector))

(defn get-text [selector]
  (.-innerText (get-el selector)))

(defn get-value [selector]
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

(defn clear-children!
  "Removes all children from element `el`."
  [el]
  (.replaceChildren el))

(defn set-children!
  "Sets the children of element `el` to list of elements `children`"
  [el children]
  (clear-children! el)
  (doseq [child children]
    (.appendChild el child)))

(defn set-child!
  "Convenience function to set a single child of element `el`."
  [el child]
  (set-children! el [child]))

(defn set-styles! [el styles]
  (set! (.-style (get-el el)) styles))

(defn set-html! [el html]
  (set! (.-innerHTML el) html))

(defn set-text! [el text]
  (set! (.-innerText el) text))

(defn add-listener!
  "Adds an event listener to the element specified by `sel` for events of type
   `event-type`, registering in the `state` atom."
  [state sel event-type f]
  (.addEventListener (get-el sel) event-type f)
  (swap! state update-in [:listeners sel event-type] #(cons f %))
  (get-in @state [:listeners sel]))

(defn clear-listeners!
  "Removes all listeners from the element specified by `sel` and updates the
   `state` atom. If `event-type` is passed, only listeners for the specified
   event type are removed. If only the `state` atom is passed, removes all
   listeners on all elements."
  ([state]
   (doseq [[sel _] (:listeners @state)]
     (clear-listeners! state sel)))
  ([state sel]
   (doseq [[event-type _] (get-in @state [:listeners sel])]
     (clear-listeners! state sel event-type)))
  ([state sel event-type]
   (let [el (get-el sel)]
     (doseq [f (get-in @state [:listeners sel event-type])]
       (.removeEventListener el event-type f)))
   (swap! state update-in [:listeners sel] dissoc event-type)
   (get-in @state [:listeners sel])))
