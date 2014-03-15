# visuals

A GUI library based on reactor, metam and JavaFX.

Currently this is purely experimental stuff!

See the [sample](javafx/src/visuals/javafx/sample.clj) to get a first idea.


## Motivation

The interesting thing about rich clients is that they combine some of the nastiest
characteristics that programming has to offer:

 - UI Toolkits like Swing, JavaFX or others are packed with mutable state. 
 - The layout and configuration of widgets is usually very verbose (or requires
   the time-consuming use of point-and-click designer tools).
 - Every piece of logic is triggered on the basis of callbacks.
 - Actions that change UI must do so by causing side effects. 
 - To make a UI responsive you have to deal with multi-threading.
 - Testing presentation logic is often very hard if it's mixed up
   with code that accesses UI. This forces you to employ UI robots and to maintain 
   brittle test scripts.

To cut it short, there are quite some challenges ahead. This library explores practical
answers to the following questions:

 - Where does functional reactive programming help?
 - Can we really escape callback hell?
 - To what extent can side effects be avoided?
 - How does a concise textual specification of UI look like?
 

## Concepts

A *Spec* is a textual model of a GUI form. An example looks like this:

```clojure
(def details-panel
  (f/panel "Address Details" :lygeneral "wrap 2" :components
           [(f/textfield "Name")
            (f/textfield "Street")
            (f/dropdownlist "Zipcode")
            (f/textfield "City")
            (f/textfield "Birthday")
            (f/button "Ok" :text "OK" :lyhint "skip")]))
```

A *Toolkit* provides the technical infrastructure and components.
It's represented by the `visuals.utils.Toolkit` protocol. 
Implementations directly access JavaFX, Swing or SWT.

A *Visual Component* is a toolkit specific GUI component like a textfield, button or a window.
It's represented by the `visuals.core.VisualComponent` protocol. Each visual component has a name
that serves as it's identifier within its parent component.

A *Component Path* is a vector of component names pointing to a visual component within the tree of components. 
If the path is denoted as string instead of a vector then only the component name is used to find
a matching component.

A *Signal* is a time-varying value, or -- in Java terms -- a mutable property. Properties of
visual components are accessible through the Signal protocol (see reactor.core).

An *Event* is a map:
 - ::sourcepath -- A component path and a conjoined keyword denoting the cause of the event.
 - ::eventtarget -- The name of the spec root used for delivering the event to the correct event handler.
 - ::payload -- An arbitrary value.

An *Event Source* is a stream of occurences. An occurence is a pair of timestamp and event.
For example button presses or changes of signals can be represented by events (see reactor.core).

A *Signal Path* is a pair of component path and keyword representing the signal.

A *View* represents the current state of a visual component tree as a map.
The map contains the following keys (all in visuals.core namespace):

 - ::spec -- The map representing the specification of the form.
 - ::vc -- The toolkits root component of the built visual component tree.
 - ::comp-map -- A map of component path to visual component for all visual components including the root.
 - ::domain-data -- A map with the business domain data.
 - ::domain-data-mapping -- A vector of mappings between signals and business domain data.
 - ::ui-state -- A map with the UI state of visual components like enabled, editable, visible etc.
 - ::ui-state-mapping -- A vector of mappings between signals and ui state data.
 - ::handler-fn -- The single handler function, which accepts the view and the event.
 - ::eventsource -- An event source that merges all events.
 - ::validation-rule-set -- A map with the set of constraints for validation.
 - ::validation-results -- A map of the current validation results.
 - ::pending-events -- A vector of events that will be delivered upon next sync with the UI.
 - ::all-views -- A map of all other visible views. The :name of the root spec is used as key.

The distinction between *domain data* and *UI state* is somewhat arbitrary. The idea is that domain data
holds the business related data as actually used in the domain layer of the system. UI state is then
all additional data that has a meaning only in the UI layer. The mappings between visual components
are kept in the corresponding -mapping slots of the view map.

An *Event Handler* is a function that handles all events for a view. It takes two
arguments: the view (see below) and the event (see above). It returns a new version of the
view.

```clojure
(defn details-handler 
  [view evt] 
  view)
```


## API overview

TODO


## Ingredients of advanced GUI
Visuals consists of distinct parts, some of which are independent libraries:

 - Reactive core ([reactor](https://github.com/friemen/reactor))
 - Capable UI toolkit and set of widgets (currently only *JavaFX*)
 - Data Validation ([examine](https://github.com/friemen/examine))
 - Declarative UI form specification
   (for grammar see [visuals.forml namespace](core/src/visuals/forml.clj), 
   based on [metam](https://github.com/friemen/metam),
   single layout manager is the awesome [MigLayout](http://www.miglayout.com/whitepaper.html))
 - Form builder for supported toolkits 
   (for JavaFX see [visuals.javafx.builder namespace](javafx/src/visuals/javafx/builder.clj))
 - Formatting/parsing of data (see [visuals.parsform namespace](core/src/visuals/parsform.clj))
 - Mapping of domain data to/from signals 
   (part of visuals.core namespace, uses [parsargs](https://github.com/friemen/parsargs))


## Next Todos

 - Add editable table
 - Add tree
 - Make validation error display nicer.


## License

Copyright 2013,2014 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
