---
name: pesit-expert
description: Steps to perform to fix a PeSIT issue and release a new version
---

## PeSIT specific problem solving
1. Check documentation
2. Write unit tests that cover the issue and its resolution
3. Check everything compiles
4. Update changelog and pom version of impacted module
5. Update dependency in all impacted modules
6. Create commit once the issue is fixed and tests pass
7. Check all GitHub Actions are up to date and pass
8. Push to GitHub
9. Create release
