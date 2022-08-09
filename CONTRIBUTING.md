# Contributing Guidelines

Thank you for your interest in contributing to our project. Whether it's a bug report, new feature, correction, or additional
documentation, we greatly value feedback and contributions from our community.

Please read through this document before submitting any issues or pull requests to ensure we have all the necessary
information to effectively respond to your bug report or contribution.


## Reporting Bugs/Feature Requests

We welcome you to use the GitHub issue tracker to report bugs or suggest features.

When filing an issue, please check existing open, or recently closed, issues to make sure somebody else hasn't already
reported the issue. Please try to include as much information as you can. Details like these are incredibly useful:

* A reproducible test case or series of steps
* The version of our code being used
* Any modifications you've made relevant to the bug
* Anything unusual about your environment or deployment


## Contributing via Pull Requests

Contributions via pull requests are much appreciated. Before sending us a pull request, please ensure that:

1. You are working against the latest source on the *main* branch.
2. You check existing open, and recently merged, pull requests to make sure someone else hasn't addressed the problem already.
3. You open an issue to discuss any significant work - we would hate for your time to be wasted.

To send us a pull request, please:

1. Fork the repository.
2. Modify the source; please focus on the specific change you are contributing. If you also reformat all the code, it will be hard for us to focus on your change.
   * Ensure your modifications are accompanied by a [changelog entry](#Changelog) where necessary.
4. Ensure local tests pass.
5. Commit to your fork using clear commit messages.
6. Send us a pull request, answering any default questions in the pull request interface.
7. Pay attention to any automated CI failures reported in the pull request, and stay involved in the conversation.

GitHub provides additional document on [forking a repository](https://help.github.com/articles/fork-a-repo/) and
[creating a pull request](https://help.github.com/articles/creating-a-pull-request/).

### Changelog
Merges to this repository must include one or more changelog entries which describe the modifications made.

Entries are placed in the top-level `.changes/` directory. An entry is a file containing a JSON object with the
following fields:

| Field name    | Type       | Required | Enum                                         | Description                                                                                                                                                                                                                                                                                                                                      |
|---------------|------------|----------|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`          | `string`   | yes      |                                              | A unique identifier for this entry. We recommend you generate a UUID for this field.                                                                                                                                                                                                                                                             |
| `type`        | `string`   | yes      | `bugfix`, `feature`, `documentation`, `misc` | The type of change being made.                                                                                                                                                                                                                                                                                                                   |
| `description` | `string`   | yes      |                                              | A description of the change being made.                                                                                                                                                                                                                                                                                                          |
| `issues`      | `string[]` | no       |                                              | A list of references to any related issues in the relevant repositories. A reference can be specified in several ways:<ul><li>The issue number, if local to this repository (eg. `#12345`)</li><li>A fully-qualified issue ID (eg.`awslabs/aws-sdk-kotlin#12345`)</li><li>A fully-qualified URL (eg. `https://issuetracker.com/12345`)</li></ul> |
| `module`      | `string`   | no       |                                              | The area of the code affected by your changes. If unsure, leave this value unset.                                                                                                                                                                                                                                                                |

The filename of an entry is arbitrary. We recommend `<id>.json`, where `<id>` corresponds to the `id` field of the entry
itself.

Entries in the `.changes/` directory are automatically rolled into the main `CHANGELOG.md` file in every release.

If you believe that your modifications do not warrant a changelog entry, you can add the `no-changelog` label to your
pull request. The label will suppress the CI that blocks merging in the absence of a changelog, though the reviewer(s)
of your request may disagree and ask that you add one anyway.

#### Example
```json
{
  "id": "263ea6ab-4b75-41a8-9c37-821c30d7b9e5",
  "type": "feature",
  "description": "Add multiplatform support for URL parsing.",
  "issues": [
    "awslabs/smithy-kotlin#12345"
  ]
}
```


When submitting a pull request please have your commits follow these guidelines:

### Git Commit Guidelines
This project uses [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/) for it's commit message format and expects all contributors to follow these guidelines.

Each commit message consists of a **header**, a **body** and a **footer**. The header has a special format that includes a **type**, a **scope** and a **subject**:

```
<type>(<scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>

```

Any line of the commit message should not be longer 100 characters. This allows the message to be easier to read on github as well as in various git tools.

#### Type

Must be one of the following:

- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)
- **refactor**: A code change that neither fixes a bug or adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests
- **chore**: Changes to the build process or auxiliary tools and libraries such as documentation generation
- **ci**: Changes to CI/CD scripts and tooling

#### Scope

The scope is optional but should be included when possible and refer to a module that is being touched. Examples:

- codegen
- rt (optionally the target platform e.g. rt-android)

#### Subject

The subject contains succinct description of the change:

- use the imperative, present tense: "change" not "changed" nor "changes"
- don't capitalize first letter
- no dot (.) at the end

#### Body

Just as in the **subject**, use the imperative, present tense: "change" not "changed" nor "changes" The body should include the motivation for the change and contrast this with previous behavior.

#### Footer

The footer should contain any information about **Breaking Changes** and is also the place to reference GitHub issues that this commit **Closes**.

The last line of commits introducing breaking changes should be in the form `BREAKING CHANGE: <desc>`

Breaking changes should also add an exclamation mark `!` after the type/scope (e.g. `refactor(rt)!: drop support for Android API < 20`)

## Finding contributions to work on
Looking at the existing issues is a great way to find something to contribute on. As our projects, by default, use the default GitHub issue labels (enhancement/bug/duplicate/help wanted/invalid/question/wontfix), looking at any 'help wanted' issues is a great place to start.


## Code of Conduct
This project has adopted the [Amazon Open Source Code of Conduct](https://aws.github.io/code-of-conduct).
For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq) or contact
opensource-codeofconduct@amazon.com with any additional questions or comments.


## Security issue notifications
If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public github issue.


## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.

We may ask you to sign a [Contributor License Agreement (CLA)](http://en.wikipedia.org/wiki/Contributor_License_Agreement) for larger changes.
