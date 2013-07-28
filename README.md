# visuals

GUI library based on reactor, metam and JavaFX.

Currently this is purely experimental stuff!

See the [sample](javafx/src/visuals/javafx/sample.clj) to get a first idea.


## Ingredients of advanced GUI

 - Reactive core ([reactor](https://github.com/friemen/reactor))
 - Capable UI toolkit and set of widgets (currently only *JavaFX*)
 - Data Validation ([examine](https://github.com/friemen/examine))
 - Declarative UI form specification (see visuals.forml namespace, based on [metam](https://github.com/friemen/metam))
 - Form builder for supported toolkits (see visuals.javafx.builder namespace)
 - Formatting/parsing of data (see visuals.parsform namespace)
 - Mapping of domain data to/from signals (part of visuals.core namespace, uses [parsargs](https://github.com/friemen/parsargs))


## Some ideas and questions that visuals explores

The interesting thing about rich clients is that they combine some of the nastiest
characteristics that programming has to offer:

 - UI Toolkits like Swing, JavaFX or others are packed with mutable state. 
 - The layout and configuration of widgets is usually very verbose (or requires
   the time-consuming use of point-and-click designer tools).
 - Every piece of logic is triggered on the basis of callbacks.
 - Actions that change UI must do so by causing side effects. 
 - To make an UI responsive you have to deal with multi-threading.
 - Testing presentation logic is often very hard if it's mixed up
   with code that accesses UI. This forces you to employ UI robots and to maintain 
   brittle test scripts.

To cut it short: there quite some challenges ahead. This library explores practical
solutions.

 - Where does functional reactive programming help?
 - Avoid callback hell.
 - To what extent can side effects be avoided?


## Some of the next TODOs

 - Make validation error display nicer.
 - Add list based components: dropdownlist, combobox, listbox
 - Open other window (modal and non-modal), but keep side effect out of action
 - Add table
 - Add tree


## License

Copyright 2013 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
