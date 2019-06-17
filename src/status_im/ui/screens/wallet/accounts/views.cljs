(ns status-im.ui.screens.wallet.accounts.views
  (:require-macros [status-im.utils.views :as views])
  (:require [status-im.ui.components.react :as react]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.icons.vector-icons :as icons]
            [status-im.ui.components.toolbar.styles :as toolbar.styles]
            [status-im.ui.components.colors :as colors]
            [status-im.i18n :as i18n]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.list-item.views :as list-item]
            [status-im.wallet.utils :as wallet.utils]
            [status-im.ui.components.bottom-bar.styles :as tabs.styles]
            [reagent.core :as reagent]
            [status-im.utils.money :as money]
            [re-frame.core :as re-frame]
            [status-im.ui.screens.wallet.accounts.sheets :as sheets]
            [status-im.ethereum.core :as ethereum]
            [status-im.ui.screens.wallet.accounts.styles :as styles]))

(def state (reagent/atom {:tab :assets}))

(views/defview account-card [name]
  (views/letsubs [currency        [:wallet/currency]
                  portfolio-value [:portfolio-value]
                  {:keys [address]} [:account/account]]
    [react/touchable-highlight {:on-press      #(re-frame/dispatch [:navigate-to :wallet-account])
                                :on-long-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                                    {:content        sheets/send-receive
                                                                     :content-height 130}])}
     [react/view {:style styles/card}
      [react/view {:flex-direction :row :align-items :center :justify-content :space-between}
       [react/nested-text {:style {:color colors/white-transparent :font-weight "500"}} "~"
        [{:style {:color colors/white}} portfolio-value]
        " "
        (:code currency)]
       [react/touchable-highlight {:on-press #(re-frame/dispatch [:wallet.accounts/share])}
        [icons/icon :main-icons/share {:color colors/white-transparent}]]]
      [react/view
       [react/text {:style {:color colors/white :font-weight "500"}} name]
       [react/text {:number-of-lines 1 :ellipsize-mode :middle :style {:color (colors/alpha colors/white 0.7)}}
        (ethereum/normalized-address address)]]]]))

(defn add-card []
  [react/touchable-highlight {:on-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                             {:content        sheets/add-account
                                                              :content-height 130}])}
   [react/view {:style styles/add-card}
    [react/view {:width       40 :height 40 :justify-content :center :border-radius 20
                 :align-items :center :background-color (colors/alpha colors/blue 0.1) :margin-bottom 8}
     [icons/icon :main-icons/add {:color colors/blue}]]
    [react/text {:style {:color colors/blue}} (i18n/label :t/add-account)]]])

(defn tab-title [state key label active? & [first?]]
  [react/touchable-highlight {:on-press #(swap! state assoc :tab key)}
   [react/view {:align-items :center :margin-left (if first? 0 24)}
    [react/text {:style {:font-weight "500" :color (if active? colors/black colors/gray)}} label]
    (when active?
      [react/view {:width 24 :height 3 :border-radius 4 :background-color colors/blue :margin-top 10}])]])

(defn render-asset [{:keys [icon decimals amount color value] :as token}]
  [list-item/list-item
   {:content     [react/view {:style {:margin-horizontal 16 :justify-content :center}}
                  [react/nested-text {:style {:font-weight "500"} :number-of-lines 1 :ellipsize-mode :tail}
                   (wallet.utils/format-amount amount decimals) " "
                   [{:style {:color colors/gray}} (wallet.utils/display-symbol token)]]
                  [react/text {:style {:color colors/gray} :number-of-lines 1 :ellipsize-mode :tail} (:name token)]]
    :image       (if icon
                   [list/item-image icon]
                   [chat-icon/custom-icon-view-list (:name token) color])
    :accessories [value]}])

(defn render-collectible [{:keys [name icon amount] :as collectible}]
  (let [items-number (money/to-fixed amount)
        details?     (pos? items-number)]
    [react/touchable-highlight
     (when details?
       {:on-press #(re-frame/dispatch [:show-collectibles-list collectible])})
     [list-item/list-item
      {:title       (wallet.utils/display-symbol collectible)
       :subtitle    name
       :image       [list/item-image icon]
       :accessories [items-number :chevron]}]]))

(views/defview assets-and-collections []
  (views/letsubs [{:keys [tokens nfts]} [:wallet/visible-assets-with-values]]
    (let [{:keys [tab]} @state]
      [react/view {:flex 1}
       [react/view {:flex-direction :row :margin-bottom 8 :padding-horizontal 16}
        [tab-title state :assets (i18n/label :t/wallet-assets) (= tab :assets) true]
        (when (seq nfts)
          [tab-title state :nft (i18n/label :t/wallet-collectibles) (= tab :nft)])]
       (if (= tab :assets)
         [list/flat-list {:data               tokens
                          :default-separator? false
                          :key-fn             :name
                          :footer             [react/view
                                               {:style {:height     tabs.styles/tabs-diff
                                                        :align-self :stretch}}]
                          :render-fn          render-asset}]
         [list/flat-list {:data               nfts
                          :default-separator? false
                          :key-fn             :name
                          :footer             [react/view
                                               {:style {:height     tabs.styles/tabs-diff
                                                        :align-self :stretch}}]
                          :render-fn          render-collectible}])])))

(views/defview total-value []
  (views/letsubs [currency        [:wallet/currency]
                  portfolio-value [:portfolio-value]]
    [react/view
     [react/nested-text {:style {:font-size 32 :color colors/gray :font-weight "600"}} "~"
      [{:style {:color colors/black}} portfolio-value]
      " "
      (:code currency)]
     [react/text {:style {:color colors/gray}} (i18n/label :t/wallet-total-value)]]))

(views/defview accounts-options []
  (views/letsubs [{:keys [seed-backed-up?]} [:account/account]]
    [react/view {:flex-direction :row :align-items :center}
     [react/view {:flex 1 :padding-left 16}
      (when-not seed-backed-up?
        [react/view {:flex-direction :row :align-items :center}
         [react/view {:width 14 :height 14 :background-color colors/gray :border-radius 7 :align-items :center
                      :justify-content :center :margin-right 9}
          [react/text {:style {:color colors/white :font-size 13 :font-weight "700"}} "!"]]
         [react/text {:style {:color colors/gray}} (i18n/label :t/back-up-your-seed-phrase)]])]
     [react/touchable-highlight {:on-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                                {:content        (sheets/accounts-options seed-backed-up?)
                                                                 :content-height (if seed-backed-up? 190 250)}])}
      [react/view {:height          toolbar.styles/toolbar-height :width toolbar.styles/toolbar-height :align-items :center
                   :justify-content :center}
       [icons/icon :main-icons/more]]]]))

(defn accounts-overview []
  [react/view {:flex 1}
   [status-bar/status-bar]
   [react/scroll-view
    [accounts-options]
    [react/view {:margin-top 8 :padding-horizontal 16}
     [total-value]
     [react/scroll-view {:horizontal true}
      [react/view {:flex-direction :row :padding-top 16 :padding-bottom 24}
       [account-card "Status account"]
       [add-card]]]]
    [assets-and-collections]]])