Flutter Resource Ranker
=================

It is easy to overuse colors, write magical numbers or long classes. This project has a script to help you detect these.
This was inspired by a [tweet from Emma Vanbrabant](https://twitter.com/emmaguy/status/1229356566819852288), where she ranked Android color resources. Seeing the usefulness, I decided replicating and expanding for Flutter. It can do the following:

 ![GIF](screenshot.png?raw=true)
 
### What it can do
Detect color usage (either `Color(...)` or `Color primary = ...`):

- See frequency of hardcoded colors. Consider using a variable for better maintainability and multi-theme support.
- Detect colors used in few places. When Chrome was redesigned, the team discovered it had [95 shades of grey](https://medium.com/@san_toki/unboxing-chrome-f6af7b8161a2). They managed to reduce to 8. Material Design Theme [supports 12 categories of color](https://material.io/design/material-theming/implementing-your-theme.html). Try to keep it simple(r).
- See how detected colors contrast with black or white. It follows [WACG recommendations](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html) and is useful to see if a heavily used color doesn't have enough contrast with black or white.

Detect class size (counts the number of `;` inside each class):
- Inspired by Detekt [LongMethod](https://arturbosch.github.io/detekt/complexity.html#longmethod).
Useful to see who are the largest classes and if there is any that is an outlier (i.e. has many more lines of code than others).
- In the future, this script could be configured for use in a CI system to fail/reject a commit or pull request that has a huge class.

Detect magical numbers (finds every number different than [-1,0,1,2] without a `;` or `,` nearby):
- Inspired by Detekt [MagicNumber](https://arturbosch.github.io/detekt/style.html#magicnumber). Detect numbers that are not attributed to a variable or method call (as long as there is a `,` after the number). The script can be customised to be more or less strict.

Getting Started
---------------
The script was written in Kotlin and depends on JVM (for File access). If you need to install Kotlin for command line access, [check here](https://kotlinlang.org/docs/tutorials/command-line.html) (`brew install kotlin`).

The script only scans `.dart` files.

```
USAGE:
$ ./resourceranker.kts <project directory> [OPTIONS]
$ kotlinc -script resourceranker.kts <project directory> [OPTIONS]

OPTIONS:
color       How many colors you are using and how many times
contrast    How many colors and how they compare to black and white
num         How many magical numbers you are using and how many times
class       How many lines each class has.
help        Show this text.
<int>       Max limit. If 0, shows all elements. Default is 10.

EXAMPLE:
$ ./resourceranker.kts documents/project color 10
$ ./resourceranker.kts ../ class 0
$ ./resourceranker.kts ../../ contrast 5
$ ./resourceranker.kts ./ num
$ ./resourceranker.kts desktop class color 0
```

 ![GIF](contrast.png?raw=true)


Issue Tracking
-------
Found a bug? Have an idea for an improvement? Would you like to add to CI or convert to other language? Feel free to [add an issue](../../issues).

License
-------

Copyright 2020 Bernardo Ferrari.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.