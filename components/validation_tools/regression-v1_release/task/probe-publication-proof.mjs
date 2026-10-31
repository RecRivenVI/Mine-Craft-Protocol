import {sha256} from './alpha-candidate.mjs';

// This release-proof exception changes only test-probe JSON publication. The full
// reverse hash must still match every callback, counter and payload byte recorded.
const previous = String.raw`            Files.writeString(
                    output.resolve(
                            System.getProperty("validation.target")
                                    + "-command-configuration-probe.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);`;
const current = String.raw`            Path destination =
                    output.resolve(
                            System.getProperty("validation.target")
                                    + "-command-configuration-probe.json");
            Path pending = destination.resolveSibling(destination.getFileName() + ".pending");
            Files.writeString(pending, result + "\n", StandardCharsets.UTF_8);
            for (int publicationAttempt = 0; ; publicationAttempt++) {
                try {
                    Files.move(
                            pending,
                            destination,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (java.nio.file.AccessDeniedException busy) {
                    // Keep the complete old record while Windows readers temporarily map it.
                    if (publicationAttempt == 24) throw busy;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException("Probe publication interrupted", interrupted);
                    }
                }
            }`;

export function previousProbePublicationSource(source, bytes) {
  if (!/^versions\/(?:1\.20\.1-forge|1\.21\.1-neoforge|26\.1\.2-neoforge|26\.3-neoforge|26\.3-fabric)\/src\/probe\/java\/io\/github\/recrivenvi\/minecraftprotocol\/probe\/validation\/CommandConfigurationProbe\.java$/.test(source)) return undefined;
  const text = bytes.toString('utf8');
  if (!bytes.equals(Buffer.from(text)) || text.split(current).length !== 2) return undefined;
  return Buffer.from(text.replace(current, previous));
}

export function probePublicationUpdate(source, bytes, recordedDigest) {
  const previous = previousProbePublicationSource(source, bytes);
  return previous !== undefined && sha256(previous) === recordedDigest;
}
