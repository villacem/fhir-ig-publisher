This document proposes an approach to make large-scale analysis of FHIR data
accessible to a larger audience and portable between systems.

The central goal is to make FHIR data work well with the best available analytic
tools, regardless of the technology stack.

*Note*: this is a work in progress and will have breaking changes without notice.

### Problem

As the availability of FHIR data increases, there is a growing interest in
utilising it for analytic purposes. However, to use FHIR effectively analysts
require a thorough understanding of the specification, including its
conventions, semantics, and data types.

FHIR is represented as a graph of resources, each of which includes nested data
elements. There are semantics defined for references between resources, data
types, terminology, extensions, and many other aspects of the specification.

Most analytic and machine learning use cases require the preparation of FHIR
data using some sort of projection or transformation from its original form. The
task of authoring these transformations is not trivial, and there is currently
no standard mechanism for reuse.

### Solution

A standard format can be provided for defining use case-specific views of FHIR
data. Tools can be developed that transform these definitions into queries that
are capable of being executed by a wide variety of different query engines.

These views can be made available to users as an easier way to consume FHIR
data - simpler to understand and easier to process with generic analytic query
tools.

FHIR implementation guides could include definitions of simple, flattened views
that comprise essential data elements. The availability of these view
definitions will greatly reduce the need for analysts to perform repetitive and
redundant transformation tasks for common use cases.

### Non-goals

This is not a full analytic toolchain, but an attempt to adapt FHIR to such
toolchains. Therefore we scope this to create flat views of resources, and
explicitly scope out higher-level analytic capabilities since many tools do
this well today. Examples of capabilities we scope out include:

* Join operation between resources. This effort creates tabular views, but users
leverage the database engine or other tool of their choice to join them and
analyze at scale.
* Any form of data aggregation or statistical analysis.

### Requirements

The proposed system attempts to meet the following requirements:

**A portable, unambiguous specification**
Any good standard is unambiguous and portable between technology stacks,
and this is no exception.

**Leverage existing standards whenever possible**
Whenever practical we should avoid creating new standards and use existing approaches to these problems.

**Ability to select from repeated structures based on field values**
Flattened repeated structures in FHIR requires checking the content of those fields.
For example, creating a table of patient home addresses requires checking that
the address.use field is ‘home’. Similarly, a table with columns for systolic
and diastolic blood pressures needs to check the Observation.component.code fields to select them properly.

**Ability to filter based on code values or other criteria**
Many useful FHIR queries rely heavily on value sets to identify needed resources.
For instance, users may be interested in a table of statin meds for analysis,
requiring a value set of statin medication codes to allow such a flattened view
of statins. Therefore some form of value set-based filter should be used to create
the needed views.

**Support running on a wide variety of databases and tools**
There are many excellent options for large-scale data analysis and new ones
continue to be created. Our efforts here should be generalizable across tools
as much as possible.

**Support direct exports from data sources**
Some users have limited analytic needs and only need views over a small subset of
FHIR data that could be produced by a given system. Ideally a flattened FHIR definition
could be interpreted by a FHIR service so only the needed subset of data is
produced – whether directly in a tabular form or limited to the FHIR resources
needed for the views.

---

**[Next: System Layers](layers.html)**
