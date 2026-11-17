# LO5 — Code Inspection and Test Automation

## 1. Review Scope and Criteria (LO5.1)

A checklist-based code inspection was selected to complement the existing automated tests. The review focuses on R3 (financial correctness) and R7 (performance measurement), following the Software Testing LO5 tutorial and Young and Pezzè Chapter 18.

The selected review criteria are correctness, consistency with requirements, unnecessary state, measurement validity, and maintainability.

### 1.1 Inspection Findings

| ID | Component | Finding | Severity and status |
|---|---|---|---|
| RV-01 | `performance/benchmark-concurrent.ps1` | `$allResults` is cleared inside the measurement loop, discarding collected observations before aggregate statistics are calculated. | Medium; confirmed by inspection; fix pending verification |
| RV-02 | `performance/benchmark-concurrent.ps1` | PowerShell `Start-Job` introduces process creation and scheduling overhead, limiting the validity of the resulting throughput measurement as a measure of server capacity. | Measurement limitation; documented |
| RV-03 | `PortfolioValuationCalculator.java` | `PositionState.realisedPnl` is updated but never used to calculate the returned valuation. This redundant state increases maintenance complexity. | Low; improvement proposed |

### 1.2 Evaluation

The review identified an issue affecting the integrity of a supplementary benchmark and a maintainability weakness in financial calculation code.

These findings illustrate how source inspection can reveal issues that existing regression tests do not directly exercise. Neither RV-02 nor RV-03 establishes a production functional defect. RV-01 does not affect the independently executed R7 sequential performance experiment.

The proposed remediation for RV-01 is to preserve accumulated measurements and assert the expected result count. RV-03 can be addressed by removing unused state, followed by regression verification.

## 2. CI Pipeline Design (LO5.2)

The existing GitHub Actions workflow (`.github/workflows/ci.yml`) runs on pushes to `master` and `feature/**`, pull requests targeting `master`, and manual dispatch.

Its first job checks out the source, configures Temurin Java 21, and executes `./mvnw -B -ntp clean verify`. This runs the automated Java test suite, including PostgreSQL Testcontainers integration tests.

The second job builds the Docker image and depends on successful completion of the test job. This ordering prevents image construction when tests fail.

The workflow uses Maven dependency caching, read-only repository permissions, concurrency cancellation and test-report artifact upload.

## 3. Automated Testing (LO5.3)

Automated checks include JUnit unit tests, parameterised boundary and combinatorial tests, MockMvc API tests, PostgreSQL integration tests, regression tests and JaCoCo coverage collection.

The 97-test suite provides automated regression feedback across the selected requirements. JaCoCo reports execution coverage during Maven verification.

PIT mutation testing and the R7 performance benchmark are separate experiments; they are not currently mandatory CI jobs.

## 4. CI Demonstration (LO5.4)

Actual GitHub Actions executions demonstrate both failure detection and successful verification.

**Failed CI run:** [43997022868](https://github.com/without-paper/mini-aladdin/actions/runs/43997022868).

The Maven job reported 97 tests, including two failures caused by the fractional-cent valuation reconciliation defect (D-02). The dependent Docker image build was skipped.

**Successful CI run:** [44002109303](https://github.com/without-paper/mini-aladdin/actions/runs/44002109303).

The Maven job reported 97 passing tests with zero failures and errors. JaCoCo generated its report, and the dependent Docker image build completed successfully.

These executions demonstrate that the pipeline detects test regressions and gates image construction on automated verification.

The workflow does not currently perform production deployment, security testing, performance acceptance testing or mandatory mutation testing.

## 5. Limitations and Follow-up

The code review covers selected components and is not a comprehensive security or financial audit. The proposed source changes require independent verification, and the CI evidence cannot establish the absence of undetected defects.

Further improvements could include targeted static analysis, verification of review findings, preservation of coverage artifacts and risk-based acceptance checks for new test gaps.

**References:** Software Testing Tutorial LO5; Young and Pezzè Chapters 18, 23 and 24; project source code and GitHub Actions execution history.