"""Extend the current JDK's installer template with CHelper's URL protocol component."""

import argparse
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory


def prepare(java_home: Path, output: Path) -> None:
    template = "jdk/jpackage/internal/resources/main.wxs"
    # Temurin ships the jpackage resources in the runtime image with no JMODs.
    with TemporaryDirectory() as extracted:
        subprocess.run(
            [
                java_home / "bin/jimage.exe",
                "extract",
                "--dir",
                extracted,
                "--include",
                f"glob:**/{template}",
                java_home / "lib/modules",
            ],
            check=True,
        )
        source = (Path(extracted) / "jdk.jpackage" / template).read_text(
            encoding="utf-8"
        )
    feature_anchor = '<ComponentGroupRef Id="FileAssociations"/>'
    directory_anchor = '<Directory Id="TARGETDIR" Name="SourceDir"/>'
    if source.count(feature_anchor) != 1 or source.count(directory_anchor) != 1:
        raise RuntimeError(
            "The JDK Windows installer template has changed; review the protocol integration."
        )
    source = source.replace(
        feature_anchor,
        feature_anchor + '\n      <ComponentRef Id="CHelperUrlProtocol"/>',
    )
    source = source.replace(
        directory_anchor,
        directory_anchor + r"""
    <DirectoryRef Id="INSTALLDIR">
      <Component Id="CHelperUrlProtocol" Guid="F5C85EB7-23EB-46C6-8975-E236901C9579" Win64="yes">
        <RegistryKey Root="HKMU" Key="Software\Classes\chelper">
          <RegistryValue Type="string" Value="URL:CHelper command library" KeyPath="yes"/>
          <RegistryValue Name="URL Protocol" Type="string" Value=""/>
          <RegistryKey Key="DefaultIcon">
            <RegistryValue Type="string" Value="&quot;[INSTALLDIR]$(var.JpAppName).exe&quot;,0"/>
          </RegistryKey>
          <RegistryKey Key="shell\open\command">
            <RegistryValue Type="string" Value="&quot;[INSTALLDIR]$(var.JpAppName).exe&quot; &quot;%1&quot;"/>
          </RegistryKey>
        </RegistryKey>
      </Component>
    </DirectoryRef>
""",
    )
    output.mkdir(parents=True, exist_ok=True)
    (output / "main.wxs").write_text(source, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.java_home, args.output)
