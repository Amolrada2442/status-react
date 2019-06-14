(ns status-im.ui.screens.intro.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.react-native.resources :as resources]
            [status-im.privacy-policy.core :as privacy-policy]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.utils.identicon :as identicon]
            [status-im.ui.components.radio :as radio]
            [taoensso.timbre :as log]
            [status-im.utils.gfycat.core :as gfy]
            [status-im.ui.components.colors :as colors]
            [reagent.core :as r]
            [status-im.ui.components.toolbar.actions :as actions]
            [status-im.ui.components.common.common :as components.common]
            [status-im.ui.screens.intro.styles :as styles]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.i18n :as i18n]
            [status-im.ui.components.status-bar.view :as status-bar]))

(defn dots-selector [{:keys [on-press n selected color]}]
  [react/view {:style (styles/dot-selector n)}
   (doall
    (for [i (range n)]
      ^{:key i}
      [react/view {:style (styles/dot color (selected i))}]))])

(defn intro-viewer [slides window-width]
  (let [margin 24
        view-width  (- window-width (* 2 margin))
        scroll-x (r/atom 0)
        scroll-view-ref (atom nil)
        max-width (* view-width (dec (count slides)))]
    (fn []
      [react/view {:style {:margin-horizontal 32
                           :align-items :center
                           :justify-content :flex-end}}
       [react/scroll-view {:horizontal true
                           :paging-enabled true
                           :ref #(reset! scroll-view-ref %)
                           :shows-vertical-scroll-indicator false
                           :shows-horizontal-scroll-indicator false
                           :pinch-gesture-enabled false
                           :on-scroll #(let [x (.-nativeEvent.contentOffset.x %)]
                                         (cond (> x max-width)
                                               (.scrollTo @scroll-view-ref (clj->js {:x 0}))
                                               (< x 0)
                                               (.scrollTo @scroll-view-ref (clj->js {:x max-width}))
                                               :else (reset! scroll-x x)))
                           :style {:width view-width
                                   :margin-vertical 32}}
        (for [s slides]
          ^{:key (:title s)}
          [react/view {:style {:width view-width
                               :padding-horizontal 16}}
           [react/view {:style styles/intro-logo-container}
            [components.common/image-contain
             {:container-style {}}
             {:image (:image s) :width view-width  :height view-width}]]
           [react/i18n-text {:style styles/wizard-title :key (:title s)}]
           [react/i18n-text {:style styles/wizard-text
                             :key   (:text s)}]])]
       (let [selected (hash-set (/ @scroll-x view-width))]
         [dots-selector {:selected selected :n (count slides)
                         :color colors/blue}])])))

(defview intro []
  (letsubs [window-width [:dimensions/window-width]]
    [react/view {:style styles/intro-view}
     [status-bar/status-bar {:flat? true}]
     [intro-viewer [{:image (:intro1 resources/ui)
                     :title :intro-title1
                     :text :intro-text1}
                    {:image (:intro2 resources/ui)
                     :title :intro-title2
                     :text :intro-text2}
                    {:image (:intro3 resources/ui)
                     :title :intro-title3
                     :text :intro-text3}] window-width]
     [react/view styles/buttons-container
      [components.common/button {:button-style (assoc styles/bottom-button :margin-bottom 16)
                                 :on-press     #(re-frame/dispatch [:accounts.create.ui/intro-wizard])
                                 :label        (i18n/label :t/get-started)}]
      [components.common/button {:button-style (assoc styles/bottom-button :margin-bottom 24)
                                 :on-press    #(re-frame/dispatch [:accounts.recover.ui/recover-account-button-pressed])
                                 :label       (i18n/label :t/access-key)
                                 :background? false}]
      [react/nested-text
       {:style styles/welcome-text-bottom-note}
       (i18n/label :t/intro-privacy-policy-note1)
       [{:style (assoc styles/welcome-text-bottom-note :color colors/blue)
         :on-press privacy-policy/open-privacy-policy-link!}
        (i18n/label :t/intro-privacy-policy-note2)]]]]))

(defn generate-key []
  [components.common/image-contain
   {:container-style {:margin-horizontal 80}}
   {:image (resources/get-image :sample-key)
    :width 154 :height 140}])

(defn choose-key [{:keys [accounts selected-pubkey] :as wizard-state} view-height]
  [react/scroll-view {:content-container-style {:flex 1
                                                :justify-content :flex-end
                                                ;; We have to align top account entry
                                                ;; with top key storage entry on the next screen
                                                :margin-bottom (if (< view-height 600)
                                                                 -20
                                                                 (/ view-height 12))}}
   (for [acc accounts]
     (let [selected? (= (:pubkey acc) selected-pubkey)]
       ^{:key (:pubkey acc)}
       [react/touchable-highlight
        {:on-press #(re-frame/dispatch [:intro-wizard/on-key-selected (:pubkey acc)])}
        [react/view {:style (styles/list-item selected?)}

         [react/image {:source {:uri (identicon/identicon (:pubkey acc))}
                       :style styles/account-image}]
         [react/view {:style {:margin-horizontal 16 :flex 1}}
          [react/view {:style {:justify-content :center}}
           [react/text {:style (assoc styles/wizard-text :text-align :left
                                      :color colors/black
                                      :font-weight "500")
                        :number-of-lines 1
                        :ellipsize-mode :middle}
            (gfy/generate-gfy (:pubkey acc))]]
          [react/view {:style {:justify-content :center}}
           [react/text {:style (assoc styles/wizard-text :text-align :left)
                        :number-of-lines 1
                        :ellipsize-mode :middle}
            (:pubkey acc)]]]
         [radio/radio selected?]]]))])

(defn storage-entry [{:keys [type icon title desc]} selected-storage-type]
  (let [selected? (= type selected-storage-type)]
    [react/view
     [react/view {:style {:padding-top 14 :padding-bottom 4}}
      [react/text {:style (assoc styles/wizard-text :text-align :left :margin-left 16)}
       (i18n/label type)]]
     [react/touchable-highlight
      {:on-press #(re-frame/dispatch [:intro-wizard/on-key-storage-selected type])}
      [react/view (assoc (styles/list-item selected?)
                         :align-items :flex-start
                         :padding-top 20
                         :padding-bottom 12)
       [vector-icons/icon icon {:color (if selected? colors/blue colors/gray)
                                :width 24 :height 24}]
       [react/view {:style {:margin-horizontal 16 :flex 1}}
        [react/view {:style {}}
         [react/text {:style (assoc styles/wizard-text :font-weight "500" :color colors/black :text-align :left)}
          (i18n/label title)]]
        [react/text {:style (assoc styles/wizard-text :text-align :left)}
         (i18n/label desc)]]
       [radio/radio selected?]]]]))

(defn select-key-storage [{:keys [selected-storage-type] :as wizard-state} view-height]
  (let [storage-types [{:type :default
                        :icon :main-icons/mobile
                        :title :this-device
                        :desc :this-device-desc}
                       {:type :advanced
                        :icon :main-icons/keycard-logo
                        :title :keycard
                        :desc :keycard-desc}]]
    [react/view {:style {:flex 1
                         :justify-content :flex-end
                         ;; We have to align top storage entry
                         ;; with top account entry on the previous screen
                         :margin-bottom (+ (- 300 224) (if (< view-height 600)
                                                         -20
                                                         (/ view-height 12)))}}
     [storage-entry (first storage-types) selected-storage-type]
     [react/view {:style {:min-height 16 :max-height 16}}]
     [storage-entry (second storage-types) selected-storage-type]]))

(defn password-container [confirm-failure?]
  [react/view {:style {:flex 1
                       :justify-content :space-between
                       :align-items :center :margin-horizontal 16}}
   [react/view {:style {:justify-content :center :flex 1}}
    [react/text {:style (assoc styles/wizard-text :color colors/red
                               :margin-bottom 16)}
     (if confirm-failure? (i18n/label :t/passcode-error) " ")]

    [react/text-input {:secure-text-entry true
                       :auto-focus true
                       :text-align :center
                       :placeholder ""
                       :style styles/password-text-input
                       :on-key-press #(re-frame/dispatch [:intro-wizard/code-symbol-pressed (.-key (.-nativeEvent %))])}]]
   [react/text {:style (assoc styles/wizard-text :margin-bottom 16)} (i18n/label :t/password-description)]])

(defn create-code [{:keys [confirm-failure?] :as wizard-state}]
  [password-container confirm-failure?])

(defn confirm-code [{:keys [confirm-failure?] :as wizard-state}]
  [password-container confirm-failure?])

(defn enable-fingerprint []
  [vector-icons/icon :main-icons/fingerprint {:container-style {:align-items :center  :justify-content :center}
                                              :color colors/blue}])

(defn enable-notifications []
  [vector-icons/icon :main-icons/bell {:container-style {:align-items :center  :justify-content :center}
                                       :color colors/white}])

(defn bottom-bar [{:keys [step generating-keys? encrypt-with-password?] :as wizard-state}]
  [react/view {:style {:margin-bottom (if (or (#{2 3} step)
                                              (and (#{4 5} step)
                                                   encrypt-with-password?))
                                        20
                                        32)
                       :align-items :center}}
   (cond generating-keys?
         [react/activity-indicator {:animating true
                                    :size      :large}]
         (#{1 6 7} step)
         (let [label-kw (case step
                          1 :generate-a-key
                          6 :intro-wizard-title6
                          7 :intro-wizard-title7)]
           [components.common/button {:button-style styles/bottom-button
                                      :on-press     #(re-frame/dispatch
                                                      [:intro-wizard/step-forward-pressed])
                                      :label        (i18n/label label-kw)}])
         (and (#{4 5} step)
              (not encrypt-with-password?))
         [components.common/button {:button-style styles/bottom-button
                                    :label (i18n/label :t/encrypt-with-password)
                                    :on-press #(re-frame/dispatch [:intro-wizard/on-encrypt-with-password-pressed])
                                    :background? false}]

         :else
         [react/view {:style styles/bottom-arrow}
          [components.common/bottom-button {:on-press     #(re-frame/dispatch
                                                            [:intro-wizard/step-forward-pressed])
                                            :forward? true}]])
   (when (#{6 7} step)
     [components.common/button {:button-style (assoc styles/bottom-button :margin-top 20)
                                :label (i18n/label :t/maybe-later)
                                :on-press #(re-frame/dispatch [:intro-wizard/step-forward-pressed {:skip? true}])
                                :background? false}])
   (when (= 1 step)
     [react/text {:style (assoc styles/wizard-text :margin-top 20)}
      (i18n/label (if generating-keys? :t/generating-keys
                      :t/this-will-take-few-seconds))])])

(defn top-bar [{:keys [step encrypt-with-password?]}]
  (let [hide-subtitle? (or (= step 5)
                           (and (#{4 5} step) encrypt-with-password?))]
    [react/view {:style {:margin-top   16
                         :margin-horizontal 32}}

     [react/text {:style (cond-> styles/wizard-title
                           hide-subtitle?
                           (assoc :margin-bottom 0))}
      (i18n/label (keyword (str "intro-wizard-title" (when  (and (#{4 5} step) encrypt-with-password?)
                                                       "-alt") step)))]
     (cond (#{2 3} step)
           ; Use nested text for the "Learn more" link
           [react/nested-text {:style styles/wizard-text}
            (str (i18n/label (keyword (str "intro-wizard-text" step))) " ")
            [{:on-press #(re-frame/dispatch [:bottom-sheet/show-sheet :learn-more
                                             {:title (i18n/label (if (= step 2) :t/about-names-title :t/about-key-storage-title))
                                              :content  (i18n/label (if (= step 2) :t/about-names-content :t/about-key-storage-content))}])
              :style {:color colors/blue}}
             (i18n/label :learn-more)]]
           (not hide-subtitle?)
           [react/text {:style styles/wizard-text}
            (i18n/label (keyword (str "intro-wizard-text" step)))]
           :else nil)]))

(defview wizard []
  (letsubs [{:keys [step generating-keys?] :as wizard-state} [:intro-wizard]
            {view-height :height} [:dimensions/window]]
    [react/keyboard-avoiding-view {:style {:flex 1}}
     [toolbar/toolbar
      {:style {:border-bottom-width 0
               :margin-top 0}}
      (when-not (#{6 7} step)
        (toolbar/nav-button
         (actions/back #(re-frame/dispatch
                         [:intro-wizard/step-back-pressed]))))
      nil]
     [react/view {:style {:flex 1
                          :justify-content :space-between}}
      [top-bar wizard-state]
      (case step
        1 [generate-key]
        2 [choose-key wizard-state view-height]
        3 [select-key-storage wizard-state view-height]
        4 [create-code wizard-state]
        5 [confirm-code wizard-state]
        6 [enable-fingerprint]
        7 [enable-notifications]
        nil nil)
      [bottom-bar wizard-state]]]))
