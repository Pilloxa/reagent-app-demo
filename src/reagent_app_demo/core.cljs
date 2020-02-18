(ns reagent-app-demo.core
  (:require [reagent.core :as r]
            [cljs.spec.alpha :as s]
            [orchestra-cljs.spec.test :as st]
            [clojure.test.check.generators]
            [cljs.spec.gen.alpha :as gen]))

;;
;; imports
;;

(def expo (js/require "expo"))
(def react-native (js/require "react-native"))
(def victory (js/require "victory-native"))
(def view (r/adapt-react-class (.-View react-native)))
(def text (r/adapt-react-class (.-Text react-native)))
(def touchable-opacity (r/adapt-react-class (.-TouchableOpacity react-native)))
(def chart (r/adapt-react-class (.-VictoryChart victory)))
(def chart-line (r/adapt-react-class (.-VictoryLine victory)))

;;
;; db
;;

(s/def :patient/id keyword?)
(s/def :patient/selected? boolean?)
(s/def :patient/dose-history (s/every (s/int-in 0 100)
                                      :min-count 4
                                      :max-count 4))
(s/def :patient/color
  (s/with-gen
    (s/and string? #(re-matches #"rgb\(\d+,\d+,\d+\)" %))
    #(gen/fmap (fn [[r g b]] (str "rgb(" r "," g "," b ")"))
               (s/gen (s/tuple (s/int-in 0 255) (s/int-in 0 255) (s/int-in 0 255))))))

(s/def ::patient (s/keys :req [:patient/id
                               :patient/color
                               :patient/selected?
                               :patient/dose-history]))

(s/def :db/patients (s/every ::patient))

(s/def ::app-db (s/keys :req [:db/patients]))

(defonce app-db
  (r/atom
    #:db {:date-range ["2019-06-01" "2019-06-02" "2019-06-03" "2019-06-04"]
          :patients   [#:patient {:id           :patient-1
                                  :dose-history [(rand-int 100) (rand-int 100) (rand-int 100) (rand-int 100)]
                                  :color        "rgb(255,0,0)"
                                  :selected?    true}
                       #:patient {:id           :patient-2
                                  :dose-history [92 93 95 95]
                                  :color        "rgb(0,0,255)"
                                  :selected?    true}]}))

;;
;; db fns
;;

(defn get-date-range [db]
  (:db/date-range db))

(defn get-patients [db]
  (:db/patients db))

(s/fdef get-patients
        :args (s/cat :db ::app-db)
        :ret (s/every ::patient))

(defn get-selected-patients [db]
  (->> db
       (get-patients)
       (filter :patient/selected?)))

(s/fdef get-selected-patients
        :args (s/cat :db ::app-db)
        :ret (s/every ::patient))

(defn toggle-select-patient [patient-id]
  (swap! app-db update :db/patients
         (fn [patients]
           (mapv (fn [{:patient/keys [id] :as patient}]
                   (if (= id patient-id)
                     (update patient :patient/selected? not)
                     patient))
                 patients))))

(s/fdef toggle-select-patient
        :args (s/cat :patient-id :patient/id)
        :ret ::app-db)

;;
;; components
;;

(defn checkbox [selected?]
  [view {:style {:background-color (if selected? "#0377fc" :white)
                 :align-items      :center
                 :justify-content  :center
                 :width            20
                 :height           20
                 :margin           5
                 :border-color     :black
                 :border-width     1
                 :border-radius    3}}
   [text {:style {:color       :white
                  :font-weight :bold
                  :font-size   14}}
    (if selected? "X" "")]])

(defn patient-selector [{:patient/keys [id color selected?]}]
  [touchable-opacity {:on-press #(toggle-select-patient id)
                      :style    {:display        :flex
                                 :flex-direction :row
                                 :align-items    :center
                                 :margin-top     8}}
   [view {:style {:width            10
                  :height           30
                  :background-color color}}]
   [checkbox selected?]
   [text
    id]])

(defn patient-selectors []
  (let [patients (get-patients @app-db)]
    [view {:style {:display         :flex
                   :flex-direction  :column
                   :justify-content :space-around}}
     [view {:style {:display        :flex
                    :flex-direction :column}}
      (for [patient patients]
        ^{:key (:patient/id patient)}
        [patient-selector patient])]]))

(defn dose-chart []
  (let [date-range        (get-date-range @app-db)
        selected-patients (get-selected-patients @app-db)]
    [chart
     (for [{:patient/keys [id color dose-history]} selected-patients]
       ^{:key id}
       [chart-line {:style   {:data {:stroke       color
                                     :stroke-width 5}}
                    :animate {:duration 200
                              :on-load  {:duration 200}}
                    :data    (map-indexed (fn [i v]
                                            {:x (get date-range i)
                                             :y v})
                                          dose-history)}])]))

(defn app-root []
  [view {:style {:flex             1
                 :background-color :white
                 :justify-content  :center
                 :align-items      :center}}
   [dose-chart]
   [patient-selectors]])

(defn init []
  (st/instrument)
  (.registerRootComponent expo (r/reactify-component app-root)))























(comment
  (gen/generate (s/gen ::patient))
  (gen/generate (s/gen :db/patients))
  )
