# visuals

GUI library based on reactor, metam and JavaFX.

Currently this is purely experimental stuff!

See the [sample](javafx/src/visuals/javafx/sample.clj) to get a first idea.


## Ingredients of advanced GUI

 - Reactive core (*reactor*)
 - Capable UI toolkit and set of widgets (*JavaFX*)
 - Data Validation (*examine*, not yet used)
 - Declarative UI form specification (visuals.forml ns, based on *metam*)
 - Form builder for supported toolkits (visuals.javafx.builder ns)
 - Formatting/parsing of data (currently missing)
 - Mapping of domain data to/from signals (part of visuals.core ns)

 
## License

Copyright © 2013 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
