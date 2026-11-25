package app.trierarch.feature.rootfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LxcCatalogTest {
    private val index = """
        {
          "products": {
            "alpine:3.22:arm64:default": {
              "arch": "arm64",
              "os": "Alpine",
              "release": "3.22",
              "variant": "default",
              "versions": {
                "20261116_13:01": {
                  "items": {
                    "root.tar.xz": {
                      "sha256": "old-sha",
                      "size": 10,
                      "path": "images/alpine/3.22/arm64/default/20261116_13:01/rootfs.tar.xz"
                    }
                  }
                },
                "20261117_13:01": {
                  "items": {
                    "root.tar.xz": {
                      "sha256": "new-sha",
                      "size": 20,
                      "path": "images/alpine/3.22/arm64/default/20261117_13:01/rootfs.tar.xz"
                    },
                    "root.squashfs": {
                      "sha256": "squash",
                      "size": 30,
                      "path": "images/alpine/3.22/arm64/default/20261117_13:01/rootfs.squashfs"
                    }
                  }
                }
              }
            },
            "archlinux:current:arm64:default": {
              "arch": "arm64",
              "os": "Archlinux",
              "release": "current",
              "variant": "default",
              "versions": {
                "20261118_04:18": {
                  "items": {
                    "root.tar.xz": {
                      "sha256": "arch-sha",
                      "size": 300,
                      "path": "images/archlinux/current/arm64/default/20261118_04:18/rootfs.tar.xz"
                    }
                  }
                }
              }
            },
            "debian:12:arm64:cloud": {
              "arch": "arm64",
              "os": "Debian",
              "release": "12",
              "variant": "cloud",
              "versions": {
                "20261118_05:24": {
                  "items": {
                    "root.tar.xz": {
                      "sha256": "debian-sha",
                      "size": 400,
                      "path": "images/debian/12/arm64/cloud/20261118_05:24/rootfs.tar.xz"
                    }
                  }
                }
              }
            },
            "ubuntu:noble:amd64:default": {
              "arch": "amd64",
              "os": "Ubuntu",
              "release": "noble",
              "variant": "default",
              "versions": {
                "20261118_07:42": {
                  "items": {
                    "root.tar.xz": {
                      "sha256": "ubuntu-sha",
                      "size": 500,
                      "path": "images/ubuntu/noble/amd64/default/20261118_07:42/rootfs.tar.xz"
                    }
                  }
                }
              }
            },
            "fedora:43:arm64:default": {
              "arch": "arm64",
              "os": "Fedora",
              "release": "43",
              "variant": "default",
              "versions": {
                "20261117_21:56": {
                  "items": {
                    "root.squashfs": {
                      "sha256": "fedora-sha",
                      "size": 600,
                      "path": "images/fedora/43/arm64/default/20261117_21:56/rootfs.squashfs"
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun filtersByArchAndDefaultVariantAndKeepsOnlyTheLatestRootfs() {
        val images = LxcCatalog.parse(index, "arm64")

        assertEquals(listOf("archlinux:current:arm64:default", "alpine:3.22:arm64:default"), images.map { it.product })
        val alpine = images.single { it.product == "alpine:3.22:arm64:default" }
        assertEquals("20261117_13:01", alpine.buildDate)
        assertEquals(20L, alpine.size)
        assertEquals("new-sha", alpine.sha256)
        assertEquals(
            "https://images.linuxcontainers.org/images/alpine/3.22/arm64/default/20261117_13:01/rootfs.tar.xz",
            alpine.downloadUrl,
        )
        assertEquals("alpine-3-22-20261117-arm64.tar.xz", alpine.fileName)
        assertTrue(images.first().recommended)
    }

    @Test
    fun returnsNothingForAnArchWithNoImages() {
        assertEquals(emptyList<LxcImage>(), LxcCatalog.parse(index, "riscv64"))
    }
}
