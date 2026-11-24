# Execution plan line

Before the first test case of a suite runs, katalan prints **one stdout line** listing what is about to run, in
execution order:

```
KATALAN_PLAN {"v":1,"suite":"Regression","total":3,"testCases":["Test Cases/Login/TC01","Test Cases/Pay/TC02","Test Cases/Login/TC01"]}
```

- `testCases` uses exactly the ids printed later in the `Test Cases/<id>..........RUNNING|PASSED|FAILED` lines.
- A test case that appears twice in the suite appears twice here, and runs twice.
- Lines over 60 KB are cut to `{"v":1,"suite":...,"total":N,"testCases":[],"truncated":true}`.
- Never throws and costs one `println`; nothing is read from the browser.

A launching process (CI, dashboard, chat bot) can use it to show test cases that have **not started yet** and to
report "3 of 7 done, now running X" without waiting for each `RUNNING` line. Match runs to the plan by order:
the k-th time a path starts is the k-th occurrence of that path in the plan.

Older versions of katalan do not print this line; the suite counter (`Test Suites/<name>..........3/7(43%)`) is then the
only source of the total.
