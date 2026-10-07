//! Source documents shared by Wiki parsing and command-resource enrichment.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiCommandDocument {
    pub command: String,
    pub source_url: String,
    #[serde(default)]
    pub page_metadata: Vec<WikiPageMetadata>,
    pub intro: String,
    /// Page introductions with one explicitly named experimental feature enabled.
    #[serde(default)]
    pub experimental_intros: std::collections::BTreeMap<String, String>,
    pub parameters: Vec<WikiParameter>,
    pub syntax_descriptions: Vec<WikiSyntaxDescription>,
    #[serde(default)]
    pub passages: Vec<WikiTextPassage>,
    pub warnings: Vec<String>,
}

/// Availability banners are preserved separately from the command's purpose.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiPageMetadata {
    pub template: String,
    pub arguments: Vec<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiParameter {
    pub names: Vec<String>,
    /// Each independently declared argument heading is primary; further names
    /// within that heading are aliases. Shared coordinate headings keep both.
    #[serde(default)]
    pub primary_names: Vec<String>,
    /// A named keyword/value definition, rather than a typed input argument.
    #[serde(default)]
    pub literal: bool,
    pub types: Vec<String>,
    /// None for shared text; otherwise "be" or "je".
    pub edition: Option<String>,
    /// Section/command context from the source, for disambiguating repeated names.
    pub context: String,
    pub description: String,
    #[serde(default)]
    pub paragraphs: Vec<WikiParagraph>,
    pub brief: String,
    pub brief_explicit: bool,
    pub enumeration_intro: Option<String>,
    pub options: Vec<WikiOption>,
    /// Values explicitly documented as unsupported by this command.
    #[serde(default)]
    pub unavailable_values: Vec<String>,
    pub constraints: Vec<String>,
    /// One-based line in the patched source.
    pub line: usize,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiOption {
    pub values: Vec<String>,
    pub description: String,
    #[serde(default)]
    pub constraints: Vec<String>,
    pub options: Vec<WikiOption>,
    pub line: usize,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiParagraph {
    pub text: String,
    pub line: usize,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiTextPassage {
    pub section: String,
    pub edition: Option<String>,
    pub text: String,
    pub line: usize,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct WikiSyntaxDescription {
    pub syntax: String,
    pub description: String,
    pub brief: String,
    pub brief_explicit: bool,
    pub edition: Option<String>,
    pub context: String,
    pub line: usize,
}
