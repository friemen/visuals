# visuals

GUI library based on reactor, metam and JavaFX.

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
 

## Ingredients of advanced GUI
Visuals consists of distinct parts, some of which are independent libraries:

 - Reactive core ([reactor](https://github.com/friemen/reactor))
 - Capable UI toolkit and set of widgets (currently only *JavaFX*)
 - Data Validation ([examine](https://github.com/friemen/examine))
 - Declarative UI form specification
   (for grammar see [visuals.forml namespace](core/src/visuals/forml.clj), 
   based on [metam](https://github.com/friemen/metam))
 - Form builder for supported toolkits 
   (for JavaFX see [visuals.javafx.builder namespace](javafx/src/visuals/javafx/builder.clj))
 - Formatting/parsing of data (see [visuals.parsform namespace](core/src/visuals/parsform.clj))
 - Mapping of domain data to/from signals 
   (part of visuals.core namespace, uses [parsargs](https://github.com/friemen/parsargs))


## Next Todos

 - Automatic rule execution on change of business data.
 - Open other window (modal and non-modal), but keep side effect out of action
 - Add table
 - Add tree
 - Make validation error display nicer.


## License

Copyright 2013 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
