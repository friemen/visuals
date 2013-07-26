# visuals

GUI library based on reactor, metam and JavaFX.

Currently this is purely experimental stuff!

See the [sample](javafx/src/visuals/javafx/sample.clj) to get a first idea.


## Ingredients of advanced GUI

 - Reactive core ([reactor](https://github.com/friemen/reactor))
 - Capable UI toolkit and set of widgets (currently only *JavaFX*)
 - Data Validation ([examine](https://github.com/friemen/examine))
 - Declarative UI form specification (see visuals.forml namespace, based on [metam](https://github.com/friemen/metam))
 - Form builder for supported toolkits (see visuals.javafx.builder nampespace)
 - Formatting/parsing of data (see visuals.parsform namespace)
 - Mapping of domain data to/from signals (part of visuals.core namespace, uses [parsargs](https://github.com/friemen/parsargs))
 
## License

Copyright 2013 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
