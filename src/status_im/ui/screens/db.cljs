(ns status-im.ui.screens.db
  (:require [cljs.spec.alpha :as spec]
            [status-im.constants :as constants]
            [status-im.utils.dimensions :as dimensions]
            [status-im.fleet.core :as fleet]
            status-im.transport.db
            status-im.accounts.db
            status-im.contact.db
            status-im.ui.screens.qr-scanner.db
            status-im.ui.screens.group.db
            status-im.chat.specs
            status-im.ui.screens.profile.db
            status-im.network.module
            status-im.mailserver.db
            status-im.browser.db
            status-im.ui.screens.add-new.db
            status-im.ui.screens.add-new.new-public-chat.db
            status-im.ui.components.bottom-sheet.core
            [status-im.wallet.db :as wallet.db]))

;; initial state of app-db
(def app-db {:keyboard-height                    0
             :tab-bar-visible?                   true
             :navigation-stack                   '()
             :contacts/contacts                  {}
             :pairing/installations              {}
             :contact-recovery/pop-up            #{}
             :qr-codes                           {}
             :group/selected-contacts            #{}
             :chats                              {}
             :current-chat-id                    nil
             :selected-participants              #{}
             :sync-state                         :done
             :app-state                          "active"
             :wallet                              wallet.db/default-wallet
             :wallet/all-tokens                  {}
             :prices                             {}
             :peers-count                        0
             :node-info                          {}
             :peers-summary                      []
             :notifications                      {}
             :semaphores                         #{}
             :network                            constants/default-network
             :networks/networks                  constants/default-networks
             :my-profile/editing?                false
             :transport/chats                    {}
             :transport/filters                  {}
             :transport/message-envelopes        {}
             :mailserver/mailservers             (fleet/default-mailservers {})
             :mailserver/topics                  {}
             :mailserver/pending-requests        0
             :chat/cooldowns                     0
             :chat/cooldown-enabled?             false
             :chat/last-outgoing-message-sent-at 0
             :chat/spam-messages-frequency       0
             :tooltips                           {}
             :initial-props                      {}
             :desktop/desktop                    {:tab-view-id :home}
             :dimensions/window                  (dimensions/window)
             :push-notifications/stored          {}
             :registry                           {}
             :stickers/packs-owned               #{}
             :stickers/packs-pending            #{}
             :hardwallet                         {:nfc-supported? false
                                                  :nfc-enabled?   false
                                                  :pin            {:original     []
                                                                   :confirmation []
                                                                   :current      []
                                                                   :puk          []
                                                                   :enter-step   :original}}
             :chats/loading?                     true})

;;;;GLOBAL

(spec/def ::was-modal? (spec/nilable boolean?))
;;"http://localhost:8545"
(spec/def ::rpc-url (spec/nilable string?))
;;object? doesn't work
(spec/def ::web3 (spec/nilable any?))
(spec/def ::web3-node-version (spec/nilable string?))
;;object?
(spec/def ::webview-bridge (spec/nilable any?))
(spec/def :node/status (spec/nilable #{:stopped :starting :started :stopping}))
(spec/def :node/node-restart? (spec/nilable boolean?))
(spec/def :node/address (spec/nilable string?))

;;height of native keyboard if shown
(spec/def ::keyboard-height (spec/nilable number?))
(spec/def ::keyboard-max-height (spec/nilable number?))
(spec/def ::tab-bar-visible? (spec/nilable boolean?))
;;:online - presence of internet connection in the phone
(spec/def ::network-status (spec/nilable keyword?))

(spec/def ::app-state string?)
(spec/def ::app-in-background-since (spec/nilable number?))

;;;;NODE

(spec/def ::sync-state (spec/nilable #{:pending :in-progress :synced :done :offline}))
(spec/def ::sync-data (spec/nilable map?))

;; contents of eth_syncing or `nil` if the node isn't syncing now
(spec/def :node/chain-sync-state (spec/nilable map?))

;;;;NAVIGATION

;;current view
(spec/def :navigation/view-id (spec/nilable keyword?))
;;modal view id
(spec/def :navigation/modal (spec/nilable keyword?))
;;stack of view's ids (keywords)
(spec/def :navigation/navigation-stack (spec/nilable seq?))
(spec/def :navigation/prev-tab-view-id (spec/nilable keyword?))
(spec/def :navigation/prev-view-id (spec/nilable keyword?))
;; navigation screen params
(spec/def :navigation.screen-params/network-details (spec/keys :req [:networks/selected-network]))
(spec/def :navigation.screen-params/browser (spec/nilable map?))
(spec/def :navigation.screen-params.profile-qr-viewer/contact (spec/nilable map?))
(spec/def :navigation.screen-params.profile-qr-viewer/source (spec/nilable keyword?))
(spec/def :navigation.screen-params.profile-qr-viewer/value (spec/nilable string?))
(spec/def :navigation.screen-params/profile-qr-viewer (spec/keys :opt-un [:navigation.screen-params.profile-qr-viewer/contact
                                                                          :navigation.screen-params.profile-qr-viewer/source
                                                                          :navigation.screen-params.profile-qr-viewer/value]))
(spec/def :navigation.screen-params.qr-scanner/current-qr-context (spec/nilable any?))
(spec/def :navigation.screen-params/qr-scanner (spec/keys :opt-un [:navigation.screen-params.qr-scanner/current-qr-context]))
(spec/def :navigation.screen-params.group-contacts/show-search? (spec/nilable any?))
(spec/def :navigation.screen-params/group-contacts (spec/keys :opt [:group/contact-group-id]
                                                              :opt-un [:navigation.screen-params.group-contacts/show-search?]))
(spec/def :navigation.screen-params.edit-contact-group/group (spec/nilable any?))
(spec/def :navigation.screen-params.edit-contact-group/group-type (spec/nilable any?))
(spec/def :navigation.screen-params/edit-contact-group (spec/keys :opt-un [:navigation.screen-params.edit-contact-group/group
                                                                           :navigation.screen-params.edit-contact-group/group-type]))

(spec/def :navigation.screen-params/collectibles-list map?)

(spec/def :navigation.screen-params/show-extension map?)
(spec/def :navigation.screen-params/selection-modal-screen map?)
(spec/def :navigation.screen-params/manage-dapps-permissions map?)

(spec/def :navigation/screen-params (spec/nilable (spec/keys :opt-un [:navigation.screen-params/network-details
                                                                      :navigation.screen-params/browser
                                                                      :navigation.screen-params/profile-qr-viewer
                                                                      :navigation.screen-params/qr-scanner
                                                                      :navigation.screen-params/group-contacts
                                                                      :navigation.screen-params/edit-contact-group
                                                                      :navigation.screen-params/collectibles-list
                                                                      :navigation.screen-params/show-extension
                                                                      :navigation.screen-params/selection-modal-screen
                                                                      :navigation.screen-params/manage-dapps-permissions])))

(spec/def :desktop/desktop (spec/nilable any?))
(spec/def ::tooltips (spec/nilable any?))
(spec/def ::initial-props (spec/nilable any?))

;;;;NETWORK

(spec/def ::network (spec/nilable string?))
(spec/def ::chain (spec/nilable string?))
(spec/def ::peers-count (spec/nilable integer?))
(spec/def ::node-info (spec/nilable map?))
(spec/def ::peers-summary (spec/nilable vector?))

(spec/def ::collectible (spec/nilable map?))
(spec/def ::collectibles (spec/nilable map?))

(spec/def ::extension-url (spec/nilable string?))
(spec/def :extensions/staged-extension (spec/nilable any?))
(spec/def :extensions/manage (spec/nilable any?))
(spec/def ::extensions-store (spec/nilable any?))

;;;;NODE

(spec/def ::message-envelopes (spec/nilable map?))

;;;;UUID

(spec/def ::device-UUID (spec/nilable string?))

;;;; Supported Biometric authentication types

(spec/def ::supported-biometric-auth (spec/nilable #{:FaceID :TouchID :fingerprint}))

;;;;UNIVERSAL LINKS

(spec/def :universal-links/url (spec/nilable string?))

;; DIMENSIONS
(spec/def :dimensions/window map?)

;; PUSH NOTIFICATIONS
(spec/def :push-notifications/stored (spec/nilable map?))

(spec/def ::semaphores set?)

(spec/def ::hardwallet (spec/nilable map?))

(spec/def :stickers/packs (spec/nilable map?))
(spec/def :stickers/packs-owned (spec/nilable set?))
(spec/def :stickers/packs-pending (spec/nilable set?))
(spec/def :stickers/packs-installed (spec/nilable map?))
(spec/def :stickers/selected-pack (spec/nilable any?))
(spec/def :stickers/recent (spec/nilable vector?))
(spec/def :extensions/profile (spec/nilable any?))
(spec/def :wallet/custom-token-screen (spec/nilable map?))

(spec/def :signing/in-progress? (spec/nilable boolean?))
(spec/def :signing/queue (spec/nilable any?))
(spec/def :signing/tx (spec/nilable map?))
(spec/def :signing/sign (spec/nilable map?))
(spec/def :signing/edit-fee (spec/nilable map?))

(spec/def ::db (spec/keys :opt [:contacts/contacts
                                :contacts/new-identity
                                :contacts/new-identity-error
                                :contacts/identity
                                :contacts/ui-props
                                :contacts/list-ui-props
                                :contacts/click-handler
                                :contacts/click-action
                                :contacts/click-params
                                :pairing/installations
                                :commands/stored-command
                                :group/selected-contacts
                                :accounts/accounts
                                :accounts/create
                                :accounts/recover
                                :accounts/login
                                :account/account
                                :my-profile/profile
                                :my-profile/default-name
                                :my-profile/editing?
                                :my-profile/advanced?
                                :my-profile/seed
                                :group-chat-profile/profile
                                :group-chat-profile/editing?
                                :networks/selected-network
                                :networks/networks
                                :networks/manage
                                :bootnodes/manage
                                :extensions/staged-extension
                                :extensions/manage
                                :node/status
                                :node/restart?
                                :node/address
                                :node/chain-sync-state
                                :universal-links/url
                                :push-notifications/stored
                                :browser/browsers
                                :browser/options
                                :new/open-dapp
                                :navigation/screen-params
                                :chat/cooldowns
                                :chat/cooldown-enabled?
                                :chat/last-outgoing-message-sent-at
                                :chat/spam-messages-frequency
                                :transport/message-envelopes
                                :transport/chats
                                :transport/filters
                                :mailserver.edit/mailserver
                                :mailserver/mailservers
                                :mailserver/current-id
                                :mailserver/state
                                :mailserver/topics
                                :mailserver/pending-requests
                                :mailserver/current-request
                                :mailserver/connection-checks
                                :mailserver/request-to
                                :mailserver/request-error
                                :desktop/desktop
                                :dimensions/window
                                :dapps/permissions
                                :wallet/all-tokens
                                :ui/contact
                                :ui/search
                                :ui/chat
                                :chats/loading?
                                :stickers/packs
                                :stickers/packs-installed
                                :stickers/selected-pack
                                :stickers/recent
                                :stickers/packs-owned
                                :stickers/packs-pending
                                :bottom-sheet/show?
                                :bottom-sheet/view
                                :bottom-sheet/options
                                :extensions/profile
                                :wallet/custom-token-screen
                                :signing/in-progress?
                                :signing/queue
                                :signing/sign
                                :signing/tx
                                :signing/edit-fee]
                          :opt-un [::modal
                                   ::was-modal?
                                   ::rpc-url
                                   ::tooltips
                                   ::initial-props
                                   ::web3
                                   ::web3-node-version
                                   ::webview-bridge
                                   ::keyboard-height
                                   ::keyboard-max-height
                                   ::tab-bar-visible?
                                   ::network-status
                                   ::peers-count
                                   ::node-info
                                   ::peers-summary
                                   ::sync-state
                                   ::sync-data
                                   ::network
                                   ::chain
                                   ::app-state
                                   ::app-in-background-since
                                   ::semaphores
                                   ::hardwallet
                                   :navigation/view-id
                                   :navigation/navigation-stack
                                   :navigation/prev-tab-view-id
                                   :navigation/prev-view-id
                                   :qr/qr-codes
                                   :qr/qr-modal
                                   :qr/current-qr-context
                                   :chat/chats
                                   :chat/current-chat-id
                                   :chat/chat-id
                                   :chat/new-chat
                                   :chat/new-chat-name
                                   :chat/animations
                                   :chat/chat-ui-props
                                   :chat/chat-list-ui-props
                                   :chat/layout-height
                                   :chat/message-data
                                   :chat/message-status
                                   :chat/selected-participants
                                   :chat/public-group-topic
                                   :chat/public-group-topic-error
                                   :chat/messages
                                   :chat/message-groups
                                   :chat/message-statuses
                                   :chat/referenced-messages
                                   :chat/last-clock-value
                                   :chat/loaded-chats
                                   :chat/bot-db
                                   :chat/id->command
                                   :chat/access-scope->command-id
                                   :wallet/wallet
                                   :prices/prices
                                   :prices/prices-loading?
                                   :notifications/notifications
                                   ::device-UUID
                                   ::supported-biometric-auth
                                   ::collectible
                                   ::collectibles
                                   ::extensions-store
                                   :registry/registry]))
