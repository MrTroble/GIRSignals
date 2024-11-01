# Changelog

## [1.18.2 - 3.6.3]
* fix: Element rotation in signalbox
* fix: threads blocking each other
* ref: better system for pathway updateing and signal setting
* fix: wrong lamp in wn signal

## [1.18.2 - 3.6.2]
* feat: added train length signs
* feat: added trainnumber element
* feat: added configurable shunting pw color
* feat: added system to select pathway type when several are possible
* fix: problems with 'dead signal'
* fix: end is not a valid end point
* fix: disappearing signals in signalbox
* fix: issue in signal configs
* fix: skipping same signals
* fix: issue with names not shown every time
* fix: issue with trainnumbers
* fix: problem with shunting and normal pathway
* fix: UI line and background coloring
* fix: issue with delay intersignalbox pathway
* fix: issue with wrong feedback signal
* fix: intersignalbox can be added to saver
* fix: delay with shunting pathway
* fix: wn signals
* fix: item dropping
* fix: sh statuslight wrong lamp
* fix: issues with intersignalbox pathway
* fix: not updating signals in signalbox
* ref: remove CUSTOMNAME from saving into files
* ref: better signalbox UI
* ref: better error handling
* ref: better code performance
* ref: added arrow to possible shunting pathway end
* ref: better signalbox NBT saving
* ref: better signalbox loading system for names and signals

## [1.18.2 - 3.6.1]

* fix: issue with InterSignalBoxPathway
* fix: Zs3V on HV

## [1.18.2 - 3.6.0]

* feat: added black dots mast sign
* feat: added different sh light mast signs
* feat: Ne5 can now start and end of PW
* feat: added rectangular zs6 signs
* feat: added ne1 custom text
* feat: added one signal shape
* feat: added angled ks signals
* feat: added connectable post block
* feat: added bue identification signs
* fix: issue with doubled entries
* fix: shunting path can only be set on blocked BW
* fix: problems with shunting pathways and NPE
* ref: better handling of null check
* ref: change zs6 position

## [1.18.2 - 3.6.0 beta]

* feat: added shunting path to blocked path
* feat: added possibility to skip signals
* feat: added system to add distant signals to mainsignals without passing pathway
* feat: added overlap (protection way) system behind signals
* feat: added possiblity to rename signal on all blocks
* feat: ne, ne5 & standalone zs3 to signalbox
* feat: added standalone zs3
* feat: added end arrow
* fix: Intersignalbox pathways
* fix: more pathway issues
* fix: issue with delayable-intersignal pathway
* fix: issue with protection stop
* fix: signalconfigs, added new system
* fix: issue with signalcontroller
* ref: changed upadte system to signalstatehandler
* ref: change pathway backend system
* ref: better UI
* ref: better code performance
* ref: made corner tiles more expensive

## [1.18.2 - 3.5.0]

* feat: new path finding algorithm
* feat: added possibility to add additional costs to pathway tiles
* feat/fix: better loading system for names by signalbox
* fix: ks signalbrigde signal offsets
* fix: ks default configs
* fix: hv status light not turning off
* fix: shunting signals not turning red in partial-pathways
* fix: NPE in signalbridge
* fix: umlauts text rendering
* fix: disappearing yellow path by custom texts
* fix: disabled distant signals on shunting paths
* fix: problems with signalboxhandler files
* ref: better linkingapi classes
* ref: change error system and replace exceptions
* ref: better checker for pathways
* ref: better handling with invalid properties
* ref: improve textrenderer

**CAUTION minor braking changes! signalsystems with autoscale textrenderer needs to add +0.25 to customNameRenderHeight and may have changes for offsetY**

## [1.18.2 - 3.4.0]

* feat: added signalbridge
* feat: added signal bridge signalsystems
* feat: better ui for PathwayRequester
* feat: better ui for TrainNumber
* feat: new counter design
* feat: added sh button to signalbox
* feat: added text on ra signals
* feat/fix: umlauts text rendering
* fix: issue in signalboxhandler file
* fix: several issues in signal configs
* fix: saving block entities
* ref: update mod info
* ref: added more null checks
* ref: better code performance

## [1.18.2 - 3.3.2]

*fix: server crash when starting

## [1.18.2 - 3.3.1]

* fix: issue in SignalStateFileV2
* fix: linking list empty when copying

## [1.18.2 - 3.3.0]

* feat: new SignalFile system (V2) now in world folder and migration from old system
* feat: added train number system
* feat: added train number changing block
* fix: issue with reverse reset and rerequest of PW
* fix: missing models
* fix: scroll in Info&Help page
* fix: issue with next PWs
* fix: visual signal feedback in signalbox
* fix: issue in UIPreview

**When starting the world for the first time, the migration of the SignalFiles may take a moment.**

## [1.18.2 - 3.2.0]

* feat: added customName to signalcontroller
* feat: added system for distant signal repeater
* feat: added pathway requester
* feat/fix: hv distant signal turn off with HP0
* feat: added pathway saver
* feat: added color, when manual output is active
* feat: added level crossing function with delay and color
* feat: added intersignalbox pathways
* feat: visual signal feedback in signalbox
* feat: added counter for subsidiaries and pathway reset
* fix: issues in GUIs
* fix: issues with SignalState and NameHandler
* ref: a lot of background changes
* ref: ImageBot optimize images beep-boop
* fix: issues in configs
* fix: issue with UIClickable and Info&Help Page

## [1.18.2 - 3.1.0]

* feat: added help panel to signalbox
* feat: added shortcuts in help panel
* feat: added manipulator tool for new signal renaming
* feat: reverse resetting pathways
* feat: added tooltip to text input field
* feat: added tooltip hiding option
* feat: remove grid in usage mode from signalbox
* feat: readded configurable block light emission
* feat: configurable colors
* feat: add preview renderer
* feat: added sorting and search option in linking list
* feat: switch handle with opposide basic position
* fix: circle model
* fix: tooltip out of screen
* fix: bug in signal configs (hl, zs2v, semaphore)
* fix: redstone output of switch signals
* ref: better code performance
* ref: predicate properties

## [1.18.2 - 3.0.1]

*fix: ServerStartup issue

## [1.18.2 - 3.0.0]

* feat: added multilinking tool
* feat: added lf direction arrows
* feat: added RedstoneInput Support to SignalController
* feat: searchbar now works
* feat: custom texts in signalbox UI
* feat: added support for UTF-8 chars
* feat/fix: added system to link all redstone inputs after replacing old signalbox
* fix: relinking of redstone outputs
* fix: some signal configs
* fix: signal controller default state don't work
* fix: issue with autoPathway not working after rejoin
* fix: missing lang keys
* fix: distant signal shown custom names
* ref: better handling of autoPathway

## [Beta 11]

* feat: added sh signals and status light to subsidiary system
* feat: added autopathways
* fix: lang keys
* fix: custom name renderer
* fix: issue with too many subsidiary states
* fix: ks config
* fix: added checks for null signals and others checks
* fix: custom names
* apply 1.12 fixes
* ref: better code performance


## [Beta 5]

* feat: better network system
* fix: issue with signal config

## [1.18.2 Beta 4]

* add: missing lang keys
* fix: reset subsidiary signals
* fix: NPEs
* fix: NBT issues
* fix: performance issues and deadlock
* fix: thread issues
* fix: minor bugs 

## [1.18 Beta 3]

* fix: bugs and NPEs

  **This update is a beta version and may contains some bugs. We ask you to test the mod and give us feedback on functionality, bugs and features.**

## [1.18 Beta 2]

* fix: pathway can't be set
* fix: Issue in SignalStateHandler

  **This update is a beta version and may contains some bugs. We ask you to test the mod and give us feedback on functionality, bugs and features.**

## [1.18 Beta 1]

* feat: added text input to placementtool
* feat: resize signalbox layout to 100x100 fields
* fix: signal sounds
* fix: Nameable GUI
* fix: line renderer
* fix: typing e dont close GUI
* fix: signalbox symbols
* fix: added system to set color
* fix: render text on signals
* fix: naming of signals
* fix: NPEs
* fix: issues with ClientNameHandler and ContainerSignalBox
* fix: missing lang keys
* ref: better performance

  **This update is a beta version and may contains some bugs. We ask you to test the mod and give us feedback on functionality, bugs and features.**

## [1.18 Alpha RC3]

* fix: ra11 dwarf models
* ref: optimized models
* ref: optimized images

  **The signalbox is deactivated in this version because of UI rendering issues**

  **This update is an alpha version and does not contain the whole features. We ask you to test the mod and give us feedback on functionality, bugs and features. Warning: Contains a lot of known issues that will be fixed with coming versions.**

## [1.18 Alpha RC2]
* feat: added redstone mode to signal controller
* feat: added speed-dependent default configs for signalbox
* feat: added json-based supsidiary signals
* feat: added zs2 via signalbox
* feat: sh2 to signalbox to close track
* feat: added combi redstone input for signalbox
* feat: added system for manual redstone output
* feat: added block and item crafting system
* fix: sound registry
* fix: SignalStateFile
* fix: sync issues
* fix: npe issues
* fix: H/V zs3
* fix: some signalsystem settings
* chore: added Signalbox networking
* chore: cleanup

  **The signalbox is deactivated in this version because of UI rendering issues**

  **This update is an alpha version and does not contain the whole features. We ask you to test the mod and give us feedback on functionality, bugs and features. Warning: Contains a lot of known issues that will be fixed with coming versions.**

## [1.18-Alpha]

### BREAKING CHANGES!

* feat: added zs12
* feat: drop-down selection menu
* feat: added ContentPackSystem
* feat: new network system
* fix: serveral bugs
* chore: cleanup

  **This update is an alpha version and does not contain the whole features. We ask you to test the mod and give us feedback on functionality, bugs and features. Warning: Contains a lot of known issues that will be fixed with coming versions.**

## [Fix]
fix: desync with ForgeEssentials

## [Opt]

* optimized: image size

## [Update]
* feat: added staff uniforms and tools
* feat: double-sided text for hectometer boards
* feat: added several station and railroadcrossing signs

## [Fix]
* fix: render overlay issue
* potential fix: nbt desync (disappearing signals)

## [Fix]
* fix: missing language keys
* fix: mechanical wn signal models

## [Update]

* feat: added bue east signal
* feat: added more el signals
* feat: added more ne signals
* feat: added more other signals
* feat: added etcs signals
* feat: added ro signals
* feat: added ra11 dwarf signal
* feat: added tram signs
* feat: added railroad gates
* feat: added spring-loaded switch signals
* feat: added switch handles and mechanical wn signals
* feat: added hl block signals
* update: post block
* update: bue signs
* fix: no sounds on servers
* fix: semaphore signals with control box
* fix: ne signal heights

## [Fix]

* fix: crash on ressource pack loading

## [Update]

* feat: added new ModelRenderSystem
* feat: added semaphore signals
* feat: added andreas cross signals

## [Fix]

* fix: crash when signal is beeing destroyed
* fix: NPE with Redstone IO
* fix: Ra11 light was acitivated with train pathway

## [Fix]

* fix: partial pathways cannot be reset
* fix: partial pathways do not update correctly in UI

## [Update]

### BREAKING CHANGES!

* fix: several synchronization fixes
* feat: partly pathway resetting
* perf: reduced network load
* feat: auto recover from bad pathways
* perf: reduced redstone performance
* perf: only send needed data
* fix: improved dijkstra path detection
* feat: security patch for signal boxes (only one user at same time)
* fix: sh light signal status light
* feat: new sh light texture
* ref: optimize models

// Known issues:

* Custom names are sometimes not rendered
* Ra11 shows Ra12 with train drive path
* Edit not saved on exit (Workaround: Go to the Use tab before exiting)

## [Beta Update] 
* feat: Ra11 added to signalbox
* feat: rename signal via right click
* serveral fixes with ZS3 and ZS3_PlATE

  **Warning: Contains a lot of known issues that will be fixed with coming versions.**

## [Beta Update] 08.05.2022
* feat: ZS3_PLATE intigrated to Interoperability
* fix: various fixes
* fix: Missing KS on shunting path
* fix: KS distant with HV
* fix: Hl distant with HV
* fix: Vr with KS

## [Internal Update]

* deps: Updatet gradle wrapper and forge gradle

## [Beta Update] 05.06.2022
* feat: new HL-Signal "HL Exit"
* feat: Signalinteroperability between all signalsystems
* fix: several issues

## [Beta Update]
* fix: Missing language keys
* ref: Internal refactoring
* ci: Code checks
* fix: signal config for signalbox
* fix: desync issues
* fix: serveral issues

## [Beta Update]

* feat: Added UIs for the redstone io blocks and rename possibility
* feat: Added automatic reset of pathways
* feat: Signal Box blocks now show pathways currently in use in red

### GUI Lib

* fix: Issue with text not being shown correctly after layout calculation
* fix: Potential issue with delayed send on an invalid player
* fix: Removed debugging

## [Beta Update]

* fix: Update UI Lib to fix packet crashing
* opt: Reduced network load through compression

## [Beta Update]

* fix: Distant signals not updating
* fix: Distant signals not reseting if pathway is reset

## [Beta Update] 03.28.2022

* fix: Update issue with equals method
* feat: Signals automatically reset on link
* feat: Signals fall back to danger if signalbox is reset
* feat: Signals can now be individually removed
* feat: Signal names or type now show up while configuring
* feat: Terminus symbols can now be used as pathway destinations
* feat: Added icons for better visibility in settings page
* fix: Added paging in config
* feat: Added button for better usage in signal box
* feat: Selection of pages is now visible
* fix: Fixed synchronization issue after removing a tiles content
* feat: Added shunting pathways (SH Signals) implementation

## [Alpha Update] 03.20.2022

* Added Redstone output (e.g. switch control)
* Fixed several issues
* Signal of previous path way updated accordingly

  **This update is an alpha version and does not contain fully functional signal boxes. We ask you to test the system and give us feedback on functionality, bugs and features.**

## [Internal] 03.12.2022 - 2

* Internal Open Computers update

## [Internal] 03.12.2022 - 1

* Internal Forge version update

## [Alpha update] 02.22.2022 - 1

* Fixed correct signal aspect for hp signals
* Train routes now only applied between neighbor signals in the right direction :TM:
* Train routes can now be reset manually
* Fixed backend issues
* Fixed issue with container closed
* Fixed sync issue
* Added redstone io blocks for the signal box
* Improvements and fixes

  **This update is an alpha version and does not contain fully functional signal boxes. We ask you to test the system and give us feedback on functionality, bugs and features.**

## [Alpha update] 02.16.2022 - 1

* Added signal box

  **This update is an alpha version and does not contain fully functional signal boxes. We ask you to test the system and give us feedback on functionality, bugs and features.**

## [Update] 02.04.2022 - 1

* Imgbot optimizations

## [Update] 02.01.2022 - 1

* Readded new redstone mode interface
* Updated readme
* Added yellow-red traffic light state

## [Fix] 01.30.2022 - 1

* Fixed client sync 

## [Update/Fixes] 01.28.2022 - 1

* Updated UI system
* Updated synchronization system
* Temporarily removed Redstone mode
* Fixed various bugfixes
* Fixed HV models

## [Addition/Fix] 01.15.2022 - 1

* Fixed missing Ne2 shield for H/V signals
* Added SH status light
* Added Ra11b shunting signal
* Added Zs2v for HL and KS signals

## [Fix] 01.10.2022 - 1

* Fixed signal turning

## [Addition] 12.22.2021 - 1

* Added more HV and KS signal shields
* Added more tram signals
* Added post block

## [Addition] 11.25.2021 - 1

* Added more numbers for station number

## [Addition] 10.30.2021 - 1

* Added ZS plates for HV and KS signals

## [Fix] 9.22.2021 - 1

* Fixed Wn signal

## [Update] 8.30.2021 - 1

* Better Linking between parts
* Added linkable API

## [Addition/Fixes/Update] 8.27.2021 - 1

* Added config values for the light levels of blocks
* Reduced amount of textures
* Added emissive textures for Optifine
* Added arrow signs for NE signals
* Added station name signs

## [Minor update] 8.18.2021 - 1

* Optimized images

## [Fixes/Update] 8.10.2021 - 1

* Added additional localized information
* Fixed issue with gui size
* Streamlined UI System to give a more uniform look
* Fixed Bü4 model
* Update DE and EN language

## [Fixes/Update/Addition] 8.02.2021 - 1

* Fixed some texture flickering
* Removed unused models
* Updated SH Signal
* Added WN Signals
* Added Ra Signal (Hump Shunting Signal)
* Added ZP8 and ZP9
* Add dependency system
* Fixed issue with gui stage not being shown in renderer for controller
* Fixed signals

## [Fixes/Update/Additions] 7.31.2021 - 1

* Fixed issue with BU Lightsignal that could be without the actual signal
* Fixed issue with crash on old worlds
* Removed confusing off states
* Removed unused states
* Fixed issues with Changeable Stage
* Updated internal rendering for name renderer
* Fixed issue with height on RA signals
* In GUI renderer now shows custom name plate
* Added BUE2, BUE3 Signs
* Added Station Number Sign
* Added more ZS3 Textures
* Compacted RS Signal options

  **A lot was done to remove blank states that could confuse the user. This however could possible break some states, so you might need to reset some signals. Impacted Signals are (but not limited to): Tram Signals, RS Signals, BUE Light Signals, RA Signals, SH Signal**

## [Fixed] 7.30.2021 - 1

* Fixed issue with server client sync on redstone single mode

## [Fixed] 7.28.2021 - 2

* Fixed issue with startup

## [Hotfix] 7.28.2021 - 1

* Fixed an issue while block loading!

## [Addition] 7.24.2021 - 1

* Added Multiplex Modus to Signal Controller
(You maybe need to study IT to use it tho)

## [Update/Fixes] 7.18.2021 - 3

* Updated Controller Texture
* Updated Lang Keys
* Fixed overlapping text
* Fixed some text formatting
* Updated information display
* Fixed changelog

## [Release] 7.18.2021 - 2

First release! (Removed debugging!)

## [Fix] 7.18.2021 - 1

* Fixed version not correctly used and shown by the forge system

## [Update/Fixes] 7.15.2021 - 1

* Fixed quad color not showing in UI
* Streamlined ZS3(v)/ZS2 renderer for better performance

## [Additions] 7.11.2021 - 1

* Added preview of redstone mode
* Controller can now be customized to allow for it to be controlled via redstone

## [Fix] 6.25.2021 - 1

* Fixed potential NPE in renderer

## [Update/Fixes] 6.23.2021 - 1

* Updated UI for our signal controller
* Fixed issues with UI usage when the signal is in an unloaded chunk
* Fixed Sync issues
* Added visual feedback for changes in UI
* Fixed some properties

## [Update] 6.10.2021 - 1

* Updated UI to a more usable standard

## [Additions] 6.9.2021 - 1

* Added NE5 sign

## [Internal changes] 6.8.2021 - 1

* Model optimization

## [Additions] 6.7.2021 - 1

* Added Hectometer

## [Internal changes] 4.18.2021 - 2

* Fixed textures and particles
* NE Signs texture cleanup

## [Additions] 4.18.2021 - 1

* Added NE signs

## [Internal changes] 4.10.2021 - 1

* Texture Cleanup

## [Fixes] 4.5.2021 - 2

* Fixed crash during startup

## [Major additions] 4.5.2021 - 1

* Added RA signs
* Added BUE signs
* Added EL signs
* Added SH signs
* Added other signs
* Fixed issue with block pick
* Fixed issue with placement tools

## [Additions] 4.4.2021 - 3

* Added naming for SH signals
* Fixed issues with placement tool

## [Fixes] 4.4.2021 - 2

* Fixed issue with signal controlling out of loaded chunks
* Fixed issue with block pick returning null

## [Internal changes] 4.4.2021 - 1

## [Additions] 3.3.2021 - 1

* Added LF Signs aswell as a new placement tool
* Added new coloring API
* Signs can not be linked to a controller

## [Fixes] 19.02.2021 - 1

* Fixed issue with placement tool leading to game crash

## [Backend] 14.02.2021 - 1

* Backend changes
* Fixed issue with Controller GUI
* Fixed and other Render height issue

## [Fixes] 13.02.2021 - 1

* Fixed text renderer issue
* Fixed Bounding Box height
* Backend changes

## [Fixes] 12.02.2021 - 1

* Fixed desync issue with controller

## 1.0

Inital Curserlease
