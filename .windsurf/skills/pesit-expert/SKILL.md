---
name: pesit-expert
description: Step to perform to fix a PeSIT issue and release a new version
---

## PeSIT specific problem solving
1. check doc
2. write unit tests that cover the issue and its solving
3. check eveything compile
4. updage changelog and pom version of impacted module
5. update dependency in all impacted modules
6. create commit once the issue is fixed and tests pass
7. check all github actions are up to date and pass
8. push to github
9. create release