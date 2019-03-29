/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.content.pm.cts.shortcutmanager;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertExpectException;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.parceled;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.set;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.AssertionFailedError;

/**
 * Tests for {@link ShortcutManager} and {@link ShortcutInfo}.
 *
 * In this test, we tests the main functionalities of those, without throttling.
 */
@SmallTest
public class ShortcutManagerClientApiTest extends ShortcutManagerCtsTestsBase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected String getOverrideConfig() {
        return "reset_interval_sec=999999,"
                + "max_updates_per_interval=999999,"
                + "max_shortcuts=10"
                + "max_icon_dimension_dp=96,"
                + "max_icon_dimension_dp_lowram=96,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void testShortcutInfoMissingMandatoryFields() {

        final ComponentName mainActivity = new ComponentName(
                getTestContext().getPackageName(), "android.content.pm.cts.shortcutmanager.main");

        assertExpectException(
                RuntimeException.class,
                "id cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), null));

        assertExpectException(
                RuntimeException.class,
                "id cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), ""));

        assertExpectException(
                RuntimeException.class,
                "intents cannot contain null",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(null));

        assertExpectException(
                RuntimeException.class,
                "action must be set",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(new Intent()));

        assertExpectException(
                RuntimeException.class,
                "activity cannot be null",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setActivity(null));

        assertExpectException(
                RuntimeException.class,
                "shortLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setShortLabel(null));

        assertExpectException(
                RuntimeException.class,
                "shortLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setShortLabel(""));

        assertExpectException(
                RuntimeException.class,
                "longLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setLongLabel(null));

        assertExpectException(
                RuntimeException.class,
                "longLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setLongLabel(""));

        assertExpectException(
                RuntimeException.class,
                "disabledMessage cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setDisabledMessage(null));

        assertExpectException(
                RuntimeException.class,
                "disabledMessage cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setDisabledMessage(""));

        assertExpectException(NullPointerException.class, "action must be set",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(new Intent()));

        assertExpectException(
                IllegalArgumentException.class, "Short label must be provided", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });

        // same for add.
        assertExpectException(
                IllegalArgumentException.class, "Short label must be provided", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(mainActivity)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });

        assertExpectException(NullPointerException.class, "Intent must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                    .setActivity(mainActivity)
                    .setShortLabel("x")
                    .build();
            assertTrue(getManager().setDynamicShortcuts(list(si)));
        });

        // same for add.
        assertExpectException(NullPointerException.class, "Intent must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                    .setActivity(mainActivity)
                    .setShortLabel("x")
                    .build();
            assertTrue(getManager().addDynamicShortcuts(list(si)));
        });

        assertExpectException(
                IllegalStateException.class, "does not belong to package", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(new ComponentName("xxx", "s"))
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });

        // same for add.
        assertExpectException(
                IllegalStateException.class, "does not belong to package", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(new ComponentName("xxx", "s"))
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });

        // Not main activity
        final ComponentName nonMainActivity = new ComponentName(
                getTestContext().getPackageName(),
                "android.content.pm.cts.shortcutmanager.non_main");
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });
        // For add
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });
        // For update
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().updateShortcuts(list(si)));
                });

        // Main activity, but disabled.
        final ComponentName disabledMain = new ComponentName(
                getTestContext().getPackageName(),
                "android.content.pm.cts.shortcutmanager.disabled_main");
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });
        // For add
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });
        // For update
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().updateShortcuts(list(si)));
                });
    }

    public void testSetDynamicShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1", "title1"),
                    makeShortcut("s2", "title2"),
                    makeShortcut("s3", "title3"))));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Publish from different package.
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1x", "title1x"))));
        });

        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1x")
                    .forShortcutWithId("s1x", si -> {
                        assertEquals("title1x", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Package 1 still has the same shortcuts.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s2", "title2-updated"))));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2-updated", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list()));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .isEmpty();
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Package2 still has the same shortcuts.
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1x")
                    .forShortcutWithId("s1x", si -> {
                        assertEquals("title1x", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
    }

    public void testSetDynamicShortcuts_details() throws Exception {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon2 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_32x32));
        final Icon icon3 = loadPackageDrawableIcon(mPackageContext1, "black_64x16");
        final Icon icon4 = loadPackageDrawableIcon(mPackageContext1, "black_64x64");
        final Icon icon5 = Icon.createWithAdaptiveBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon6 = Icon.createWithAdaptiveBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_32x32));
        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo source = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setLongLabel("longlabel")
                    .setIcon(icon1)
                    .setActivity(getActivity("Launcher"))
                    .setDisabledMessage("disabledmessage")
                    .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                    .setExtras(makePersistableBundle("ek1", "ev1"))
                    .setCategories(set("cat1"))
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(source)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                        assertEquals(null, si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals(null, si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals("yyy", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals(null, si.getExtras());
                        assertEquals(null, si.getCategories());
                    });
            assertNull(
                    getIconAsLauncher(mLauncherContext1, mPackageContext1.getPackageName(), "s1"));
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo source = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setLongLabel("longlabel")
                    .setIcon(icon1)
                    .setActivity(getActivity("Launcher"))
                    .setDisabledMessage("disabledmessage")
                    .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                    .setExtras(makePersistableBundle("ek1", "ev1"))
                    .setCategories(set("cat1"))
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(source)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        // paranoid icon check
        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon2)
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon2);
        });

        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon3)
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon3);
        });


        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon4)
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon4);
        });
        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon5)
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon5);
        });
        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon6)
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                      assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon6);
        });
    }

    public void testSetDynamicShortcuts_wasPinned() throws Exception {
        // Create s1 as a floating pinned shortcut.
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s1"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            getManager().removeDynamicShortcuts(list("s1"));

            assertWith(getManager().getDynamicShortcuts())
                    .isEmpty();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1");
        });

        // Then run the same test.
        testSetDynamicShortcuts_details();
    }

    public void testAddDynamicShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcut("s1", "title1"),
                    makeShortcut("s2", "title2"),
                    makeShortcut("s3", "title3"))));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Publish from different package.
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcut("s1x", "title1x"))));
        });

        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1x")
                    .forShortcutWithId("s1x", si -> {
                        assertEquals("title1x", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Package 1 still has the same shortcuts.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcut("s2", "title2-updated"))));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2-updated", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().addDynamicShortcuts(list()));
        });

        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2", "s3")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("title1", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("title2-updated", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("title3", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Package2 still has the same shortcuts.
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1x")
                    .forShortcutWithId("s1x", si -> {
                        assertEquals("title1x", si.getShortLabel());
                    });
            assertWith(getManager().getPinnedShortcuts())
                    .isEmpty();
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
    }

    public void testAddDynamicShortcuts_details() throws Exception {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon2 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_32x32));
        final Icon icon3 = loadPackageDrawableIcon(mPackageContext1, "black_64x16");
        final Icon icon4 = loadPackageDrawableIcon(mPackageContext1, "black_64x64");

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo source = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setLongLabel("longlabel")
                    .setIcon(icon1)
                    .setActivity(getActivity("Launcher"))
                    .setDisabledMessage("disabledmessage")
                    .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                    .setExtras(makePersistableBundle("ek1", "ev1"))
                    .setCategories(set("cat1"))
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(source)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                        assertEquals(null, si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals(null, si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals("yyy", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals(null, si.getExtras());
                        assertEquals(null, si.getCategories());
                    });
            assertNull(
                    getIconAsLauncher(mLauncherContext1, mPackageContext1.getPackageName(), "s1"));
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo source = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setLongLabel("longlabel")
                    .setIcon(icon1)
                    .setActivity(getActivity("Launcher"))
                    .setDisabledMessage("disabledmessage")
                    .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                    .setExtras(makePersistableBundle("ek1", "ev1"))
                    .setCategories(set("cat1"))
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(source)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        // paranoid icon check
        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon2)
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon2);
        });

        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon3)
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon3);
        });


        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("xxx")
                    .setIntents(new Intent[]{new Intent("main").putExtra("k1", "yyy")})
                    .setIcon(icon4)
                    .build();

            assertTrue(getManager().addDynamicShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("xxx", si.getShortLabel());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon4);
        });
    }

    public void testAddDynamicShortcuts_wasPinned() throws Exception {
        // Create s1 as a floating pinned shortcut.
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s1"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            getManager().removeDynamicShortcuts(list("s1"));

            assertWith(getManager().getDynamicShortcuts())
                    .isEmpty();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1");
        });

        // Then run the same test.
        testAddDynamicShortcuts_details();
    }

    public void testUpdateShortcut() {
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1", "1a"),
                    makeShortcut("s2", "2a"),
                    makeShortcut("s3", "3a"))));
        });
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1", "1b"),
                    makeShortcut("s2", "2b"),
                    makeShortcut("s3", "3b"))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s2", "s3"), getUserHandle());
            getLauncherApps().pinShortcuts(mPackageContext2.getPackageName(),
                    list("s1", "s2"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            getManager().removeDynamicShortcuts(list("s3"));
        });
        runWithCaller(mPackageContext2, () -> {
            getManager().removeDynamicShortcuts(list("s1"));
        });

        // Check the current status.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("1a", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
                    ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3a", si.getShortLabel());
                    })
                    ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2b", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3b", si.getShortLabel());
                    })
                    ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("1b", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2b", si.getShortLabel());
                    })
                    ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // finally, call update.
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().updateShortcuts(list(
                    makeShortcut("s1", "upd1a"),
                    makeShortcut("s2", "upd2a"),
                    makeShortcut("xxx") // doen't exist -> ignored.
                    )));
        });
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().updateShortcuts(list(
                    makeShortcut("s1", "upd1b"),
                    makeShortcut("s2", "upd2b"))));
        });

        // check.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("upd1a", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("upd2a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("upd2a", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("upd2b", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3b", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("upd1b", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("upd2b", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
    }

    public void testUpdateShortcut_details() throws Exception {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon2 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_32x32));
        final Icon icon3 = loadPackageDrawableIcon(mPackageContext1, "black_64x16");
        final Icon icon4 = loadPackageDrawableIcon(mPackageContext1, "black_64x64");

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo source = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setLongLabel("longlabel")
                    .setIcon(icon1)
                    .setActivity(getActivity("Launcher"))
                    .setDisabledMessage("disabledmessage")
                    .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                    .setExtras(makePersistableBundle("ek1", "ev1"))
                    .setCategories(set("cat1"))
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(source)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            // No fields updated.
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("shortlabel", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setShortLabel("x")
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("longlabel", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setLongLabel("y")
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setActivity(getActivity("Launcher2"))
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("disabledmessage", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setDisabledMessage("z")
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("z", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("view", si.getIntents()[0].getAction());
                        assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIntents(new Intent[]{new Intent("main")})
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("z", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals(null, si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("ev1", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setExtras(makePersistableBundle("ek1", "X"))
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("z", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals(null, si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("X", si.getExtras().getString("ek1"));
                        assertEquals(set("cat1"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setCategories(set("dog"))
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("z", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals(null, si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("X", si.getExtras().getString("ek1"));
                        assertEquals(set("dog"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIcon(icon2)
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            // Check each field.
            assertWith(getManager().getDynamicShortcuts())
                    .forShortcutWithId("s1", si ->{
                        assertEquals("x", si.getShortLabel());
                        assertEquals("y", si.getLongLabel());
                        assertEquals(getActivity("Launcher2"), si.getActivity());
                        assertEquals("z", si.getDisabledMessage());
                        assertEquals(1, si.getIntents().length);
                        assertEquals("main", si.getIntents()[0].getAction());
                        assertEquals(null, si.getIntents()[0].getStringExtra("k1"));
                        assertEquals("X", si.getExtras().getString("ek1"));
                        assertEquals(set("dog"), si.getCategories());
                    });
            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon2);
        });

        // More paranoid tests with icons.
        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIcon(icon1)
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);
        });

        // More paranoid tests with icons.
        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIcon(icon3)
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon3);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIcon(icon4)
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon4);
        });

        runWithCaller(mPackageContext1, () -> {
            final ShortcutInfo updated = makeShortcutBuilder("s1")
                    .setIcon(icon1)
                    .build();

            assertTrue(getManager().updateShortcuts(list(updated)));

            assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                    icon1);

            // Extra paranoid.
            boolean success = false;
            try {
                assertIconDimensions(mLauncherContext1, mPackageContext1.getPackageName(), "s1",
                        icon2);
            } catch (AssertionFailedError expected) {
                success = true;
            }
            assertTrue(success);
        });
    }

    public void testDisableAndEnableShortcut() {
        runWithCaller(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1", "1a"),
                    makeShortcut("s2", "2a"),
                    makeShortcut("s3", "3a"))));
        });
        runWithCaller(mPackageContext2, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1", "1b"),
                    makeShortcut("s2", "2b"),
                    makeShortcut("s3", "3b"))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("s2", "s3"), getUserHandle());
            getLauncherApps().pinShortcuts(mPackageContext2.getPackageName(),
                    list("s1", "s2"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            getManager().removeDynamicShortcuts(list("s3"));
        });
        runWithCaller(mPackageContext2, () -> {
            getManager().removeDynamicShortcuts(list("s1"));
        });

        // Check the current status.
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("1a", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2b", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3b", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllEnabled()
                    .areAllPinned()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("1b", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2b", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // finally, call disable.
        runWithCaller(mPackageContext1, () -> {
            getManager().disableShortcuts(list("s1", "s3"));
        });
        runWithCaller(mPackageContext2, () -> {
            getManager().disableShortcuts(list("s1", "s2"), "custom message");
        });

        // check
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllPinned()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                        assertTrue(si.isEnabled()); // still enabled.
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3a", si.getShortLabel());
                        assertFalse(si.isEnabled()); // disabled.
                        assertNull(si.getDisabledMessage());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s3")
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3b", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllDisabled()
                    .areAllPinned()
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si -> {
                        assertEquals("1b", si.getShortLabel());
                        assertEquals("custom message", si.getDisabledMessage());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2b", si.getShortLabel());
                        assertEquals("custom message", si.getDisabledMessage());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // try re-enable
        runWithCaller(mPackageContext1, () -> {
            getManager().enableShortcuts(list("s3"));
        });
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s2")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllPinned()
                    .areAllEnabled()
                    .haveIds("s2", "s3")
                    .forShortcutWithId("s2", si -> {
                        assertEquals("2a", si.getShortLabel());
                    })
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3a", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });

        // Re-publish will implicitly re-enable.
        runWithCaller(mPackageContext2, () -> {
            getManager().addDynamicShortcuts(list(makeShortcut("s2", "re-published")));
        });

        runWithCaller(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .areAllEnabled()
                    .areAllDynamic()
                    .haveIds("s3", "s2")
                    .forShortcutWithId("s3", si -> {
                        assertEquals("3b", si.getShortLabel());
                    })
                    .forShortcutWithId("s2", si -> {
                        assertEquals("re-published", si.getShortLabel());
                    })
            ;
            assertWith(getManager().getPinnedShortcuts())
                    .areAllPinned()
                    .haveIds("s1", "s2")
            ;
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
        });
    }

    public void testImmutableShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 2,
                    "Manifest shortcuts didn't show up");
        });
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("ms21"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_2", false);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                    "Manifest shortcuts didn't show up");
        });
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCaller(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .isEmpty();
            assertWith(getManager().getPinnedShortcuts())
                    .areAllPinned()
                    .haveIds("ms21")
                    .areAllDisabled()
                    ;
            assertWith(getManager().getManifestShortcuts())
                    .areAllNotPinned()
                    .haveIds("ms1")
                    .areAllEnabled()
                    ;
        });

        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().setDynamicShortcuts(list(makeShortcut("ms1"))));
        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().setDynamicShortcuts(list(makeShortcut("ms21"))));

        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().addDynamicShortcuts(list(makeShortcut("ms1"))));
        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().addDynamicShortcuts(list(makeShortcut("ms21"))));

        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().updateShortcuts(list(makeShortcut("ms1"))));
        assertExpectException(IllegalArgumentException.class,
                "may not be manipulated via APIs",
                () -> getManager().updateShortcuts(list(makeShortcut("ms21"))));
    }

    public void testManifestDefinition() throws Exception {
        final Icon iconMs21 = loadPackageDrawableIcon(mPackageContext1, "black_16x16");

        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() > 0,
                    "Manifest shortcuts didn't show up");

            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms21", "ms22")
                    .forShortcutWithId("ms21", si-> {

                        assertEquals("Shortcut 1", si.getShortLabel());
                        assertEquals("Long shortcut label1", si.getLongLabel());
                        assertEquals(getActivity("Launcher_manifest_2"), si.getActivity());
                        assertEquals("Shortcut 1 is disabled", si.getDisabledMessage());
                        assertEquals(set("android.shortcut.conversation",
                                "android.shortcut.media"), si.getCategories());
                        assertIconDimensions(iconMs21, getIconAsLauncher(
                                mLauncherContext1, si.getPackage(), si.getId(), true));

                        // Check the intent.
                        assertEquals(1, si.getIntents().length);

                        Intent i = si.getIntents()[0];

                        assertEquals("android.intent.action.VIEW", i.getAction());
                        assertEquals(null, i.getData());
                        assertEquals(null, i.getType());
                        assertEquals(null, i.getComponent());
                        assertEquals(null, i.getExtras());
                        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                Intent.FLAG_ACTIVITY_TASK_ON_HOME,
                                i.getFlags());
                    })
                    .forShortcutWithId("ms22", si-> {
                        assertEquals("Shortcut 2", si.getShortLabel());
                        assertEquals(null, si.getLongLabel());
                        assertEquals(getActivity("Launcher_manifest_2"), si.getActivity());
                        assertEquals(null, si.getDisabledMessage());
                        assertEquals(null, si.getCategories());
                        assertNull(getIconAsLauncher(
                                mLauncherContext1, si.getPackage(), si.getId(), true));

                        // Check the intents.
                        assertEquals(2, si.getIntents().length);

                        Intent i = si.getIntents()[0];

                        assertEquals("action", i.getAction());
                        assertEquals(null, i.getData());
                        assertEquals(null, i.getType());
                        assertEquals(null, i.getComponent());
                        assertEquals(null, i.getExtras());
                        assertEquals(null, i.getCategories());
                        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                        Intent.FLAG_ACTIVITY_TASK_ON_HOME,
                                i.getFlags());

                        i = si.getIntents()[1];

                        assertEquals("action2", i.getAction());
                        assertEquals("data", i.getData().toString());
                        assertEquals("a/b", i.getType());
                        assertEquals(new ComponentName("pkg", "pkg.class"), i.getComponent());
                        assertEquals(set("icat1", "icat2"), i.getCategories());
                        assertEquals("value1", i.getStringExtra("key1"));
                        assertEquals(123, i.getIntExtra("key2", -1));
                        assertEquals(true, i.getBooleanExtra("key3", false));
                        assertEquals(0, i.getFlags());

                    })
                    ;
        });
    }

    public void testDynamicIntents() {
        runWithCaller(mPackageContext1, () -> {

            final ShortcutInfo s1 = makeShortcutBuilder("s1")
                    .setShortLabel("shortlabel")
                    .setIntents(new Intent[]{new Intent("android.intent.action.VIEW")})
                    .build();

            final Intent i1 = new Intent("action").setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            final Intent i2 = new Intent("action2").setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .setData(Uri.parse("data"))
                    .setComponent(new ComponentName("pkg", "pkg.class"))
                    .addCategory("icat1")
                    .addCategory("icat2")
                    .putExtra("key1", "value1")
                    .putExtra("key2", 123)
                    .putExtra("key3", true);

            final ShortcutInfo s2 = makeShortcutBuilder("s2")
                    .setShortLabel("shortlabel")
                    .setIntents(new Intent[]{i1, i2})
                    .build();

            assertTrue(getManager().setDynamicShortcuts(list(s1, s2)));

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2")
                    .forShortcutWithId("s1", si-> {
                        assertEquals(1, si.getIntents().length);

                        Intent i = si.getIntents()[0];

                        assertEquals("android.intent.action.VIEW", i.getAction());
                        assertEquals(null, i.getData());
                        assertEquals(null, i.getType());
                        assertEquals(null, i.getComponent());
                        assertEquals(null, i.getExtras());
                        assertEquals(0, i.getFlags());
                    })
                    .forShortcutWithId("s2", si-> {
                        assertEquals(2, si.getIntents().length);

                        Intent i = si.getIntents()[0];

                        assertEquals("action", i.getAction());
                        assertEquals(null, i.getData());
                        assertEquals(null, i.getType());
                        assertEquals(null, i.getComponent());
                        assertEquals(null, i.getExtras());
                        assertEquals(null, i.getCategories());
                        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, i.getFlags());

                        i = si.getIntents()[1];

                        assertEquals("action2", i.getAction());
                        assertEquals("data", i.getData().toString());
                        assertEquals(new ComponentName("pkg", "pkg.class"), i.getComponent());
                        assertEquals(set("icat1", "icat2"), i.getCategories());
                        assertEquals("value1", i.getStringExtra("key1"));
                        assertEquals(123, i.getIntExtra("key2", -1));
                        assertEquals(true, i.getBooleanExtra("key3", false));
                        assertEquals(Intent.FLAG_ACTIVITY_NEW_DOCUMENT, i.getFlags());
                    })
            ;
        });
    }

    public void testManifestWithErrors() {
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_error_1", true);
            enableManifestActivity("Launcher_manifest_error_2", true);
            enableManifestActivity("Launcher_manifest_error_3", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() > 0,
                    "Manifest shortcuts didn't show up");

            // Only the last one is accepted.
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("valid")
                    ;


        });
    }

    public void testManifestDisabled() {
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_4a", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() > 0,
                    "Manifest shortcuts didn't show up");

            // First they're all enabled.
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms41", "ms42", "ms43")
                    .areAllEnabled()
                    ;
        });
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCaller(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list("ms41", "ms42"), getUserHandle());
        });
        runWithCaller(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_4b", true);
            enableManifestActivity("Launcher_manifest_4a", false);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 0,
                    "Manifest shortcuts didn't update");

            // 3 was not inned, so gone.  But 1 and 2 remain.
            assertWith(getManager().getManifestShortcuts())
                    .isEmpty();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("ms41", "ms42")
                    .areAllDisabled()
                    .forShortcutWithId("ms41", si -> {
                        assertEquals(Intent.ACTION_VIEW, si.getIntent().getAction());
                    })
                    .forShortcutWithId("ms42", si -> {
                        assertEquals(Intent.ACTION_VIEW, si.getIntent().getAction());
                    })
                    ;
        });
    }

    public void testMiscShortcutInfo() {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final ShortcutInfo source = makeShortcutBuilder("s1")
                .setShortLabel("shortlabel")
                .setLongLabel("longlabel")
                .setIcon(icon1)
                .setActivity(getActivity("Launcher"))
                .setDisabledMessage("disabledmessage")
                .setIntents(new Intent[]{new Intent("view").putExtra("k1", "v1")})
                .setExtras(makePersistableBundle("ek1", "ev1"))
                .setCategories(set("cat1"))
                .build();

        final ShortcutInfo clone = parceled(source);

        // Check each field.
        assertWith(list(clone))
                .forShortcutWithId("s1", si ->{
                    assertEquals("shortlabel", si.getShortLabel());
                    assertEquals("longlabel", si.getLongLabel());
                    assertEquals(getActivity("Launcher"), si.getActivity());
                    assertEquals("disabledmessage", si.getDisabledMessage());
                    assertEquals(1, si.getIntents().length);
                    assertEquals("view", si.getIntents()[0].getAction());
                    assertEquals("v1", si.getIntents()[0].getStringExtra("k1"));
                    assertEquals("ev1", si.getExtras().getString("ek1"));
                    assertEquals(set("cat1"), si.getCategories());

                    assertEquals(getUserHandle(), si.getUserHandle());
                });
    }

    // TODO Test auto rank adjustment.
    // TODO Test save & load.
}
