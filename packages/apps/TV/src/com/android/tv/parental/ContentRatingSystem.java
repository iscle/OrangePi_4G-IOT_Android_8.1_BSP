/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.parental;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContentRating;
import android.text.TextUtils;

import com.android.tv.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ContentRatingSystem {
    /*
     * A comparator that implements the display order of a group of content rating systems.
     */
    public static final Comparator<ContentRatingSystem> DISPLAY_NAME_COMPARATOR =
            new Comparator<ContentRatingSystem>() {
                @Override
                public int compare(ContentRatingSystem s1, ContentRatingSystem s2) {
                    String name1 = s1.getDisplayName();
                    String name2 = s2.getDisplayName();
                    return name1.compareTo(name2);
                }
            };

    private static final String DELIMITER = "/";

    // Name of this content rating system. It should be unique in an XML file.
    private final String mName;

    // Domain of this content rating system. It's package name now.
    private final String mDomain;

    // Title of this content rating system. (e.g. TV-PG)
    private final String mTitle;

    // Description of this content rating system.
    private final String mDescription;

    // Country code of this content rating system.
    private final List<String> mCountries;

    // Display name of this content rating system consisting of the associated country
    // and its title. For example, "Canada (French)"
    private final String mDisplayName;

    // Ordered list of main content ratings. UX should respect the order.
    private final List<Rating> mRatings;

    // Ordered list of sub content ratings. UX should respect the order.
    private final List<SubRating> mSubRatings;

    // List of orders. This describes the automatic lock/unlock relationship between ratings.
    // For example, let say we have following order.
    //    <order>
    //        <rating android:name="US_TVPG_Y" />
    //        <rating android:name="US_TVPG_Y7" />
    //    </order>
    // This means that locking US_TVPG_Y7 automatically locks US_TVPG_Y and
    // unlocking US_TVPG_Y automatically unlocks US_TVPG_Y7 from the UX.
    // An user can still unlock US_TVPG_Y while US_TVPG_Y7 is locked by manually.
    private final List<Order> mOrders;

    private final boolean mIsCustom;

    public String getId() {
        return mDomain + DELIMITER + mName;
    }

    public String getName(){
        return mName;
    }

    public String getDomain() {
        return mDomain;
    }

    public String getTitle(){
        return mTitle;
    }

    public String getDescription(){
        return mDescription;
    }

    public List<String> getCountries(){
        return mCountries;
    }

    public List<Rating> getRatings(){
        return mRatings;
    }

    public Rating getRating(String name) {
        for (Rating rating : mRatings) {
            if (TextUtils.equals(rating.getName(), name)) {
                return rating;
            }
        }
        return null;
    }

    public List<SubRating> getSubRatings(){
        return mSubRatings;
    }

    public List<Order> getOrders(){
        return mOrders;
    }

    /**
     * Returns the display name of the content rating system consisting of the associated country
     * and its title. For example, "Canada (French)".
     */
    public String getDisplayName() {
        return mDisplayName;
    }

    public boolean isCustom() {
        return mIsCustom;
    }

    /**
     * Returns true if the ratings is owned by this content rating system.
     */
    public boolean ownsRating(TvContentRating rating) {
        return mDomain.equals(rating.getDomain()) && mName.equals(rating.getRatingSystem());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContentRatingSystem) {
            ContentRatingSystem other = (ContentRatingSystem) obj;
            return this.mName.equals(other.mName) && this.mDomain.equals(other.mDomain);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * mName.hashCode() + mDomain.hashCode();
    }

    private ContentRatingSystem(
            String name, String domain, String title, String description, List<String> countries,
            String displayName, List<Rating> ratings, List<SubRating> subRatings,
            List<Order> orders, boolean isCustom) {
        mName = name;
        mDomain = domain;
        mTitle = title;
        mDescription = description;
        mCountries = countries;
        mDisplayName = displayName;
        mRatings = ratings;
        mSubRatings = subRatings;
        mOrders = orders;
        mIsCustom = isCustom;
    }

    public static class Builder {
        private final Context mContext;
        private String mName;
        private String mDomain;
        private String mTitle;
        private String mDescription;
        private List<String> mCountries;
        private final List<Rating.Builder> mRatingBuilders = new ArrayList<>();
        private final List<SubRating.Builder> mSubRatingBuilders = new ArrayList<>();
        private final List<Order.Builder> mOrderBuilders = new ArrayList<>();
        private boolean mIsCustom;

        public Builder(Context context) {
            mContext = context;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setDomain(String domain) {
            mDomain = domain;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        public void setDescription(String description) {
            mDescription = description;
        }

        public void addCountry(String country) {
            if (mCountries == null) {
                mCountries = new ArrayList<>();
            }
            mCountries.add(new Locale("", country).getCountry());
        }

        public void addRatingBuilder(Rating.Builder ratingBuilder) {
            // To provide easy access to the SubRatings in it,
            // Rating has reference to SubRating, not Name of it.
            // (Note that Rating/SubRating is ordered list so we cannot use Map)
            // To do so, we need to have list of all SubRatings which might not be available
            // at this moment. Keep builders here and build it with SubRatings later.
            mRatingBuilders.add(ratingBuilder);
        }

        public void addSubRatingBuilder(SubRating.Builder subRatingBuilder) {
            // SubRatings would be built rather to keep consistency with other fields.
            mSubRatingBuilders.add(subRatingBuilder);
        }

        public void addOrderBuilder(Order.Builder orderBuilder) {
            // To provide easy access to the Ratings in it,
            // Order has reference to Rating, not Name of it.
            // (Note that Rating/SubRating is ordered list so we cannot use Map)
            // To do so, we need to have list of all Rating which might not be available
            // at this moment. Keep builders here and build it with Ratings later.
            mOrderBuilders.add(orderBuilder);
        }

        public void setIsCustom(boolean isCustom) {
            mIsCustom = isCustom;
        }

        public ContentRatingSystem build() {
            if (TextUtils.isEmpty(mName)) {
                throw new IllegalArgumentException("Name cannot be empty");
            }
            if (TextUtils.isEmpty(mDomain)) {
                throw new IllegalArgumentException("Domain cannot be empty");
            }

            StringBuilder sb = new StringBuilder();
            if (mCountries != null) {
                if (mCountries.size() == 1) {
                    sb.append(new Locale("", mCountries.get(0)).getDisplayCountry());
                } else if (mCountries.size() > 1) {
                    Locale locale = Locale.getDefault();
                    if (mCountries.contains(locale.getCountry())) {
                        // Shows the country name instead of "Other countries" if the current
                        // country is one of the countries this rating system applies to.
                        sb.append(locale.getDisplayCountry());
                    } else {
                        sb.append(mContext.getString(R.string.other_countries));
                    }
                }
            }
            if (!TextUtils.isEmpty(mTitle)) {
                sb.append(" (");
                sb.append(mTitle);
                sb.append(")");
            }
            String displayName = sb.toString();

            List<SubRating> subRatings = new ArrayList<>();
            if (mSubRatingBuilders != null) {
                for (SubRating.Builder builder : mSubRatingBuilders) {
                    subRatings.add(builder.build());
                }
            }

            if (mRatingBuilders.size() <= 0) {
                throw new IllegalArgumentException("Rating isn't available.");
            }
            List<Rating> ratings = new ArrayList<>();
            // Map string ID to object.
            for (Rating.Builder builder : mRatingBuilders) {
                ratings.add(builder.build(subRatings));
            }

            // Sanity check.
            for (SubRating subRating : subRatings) {
                boolean used = false;
                for (Rating rating : ratings) {
                    if (rating.getSubRatings().contains(subRating)) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    throw new IllegalArgumentException("Subrating " + subRating.getName() +
                        " isn't used by any rating");
                }
            }

            List<Order> orders = new ArrayList<>();
            if (mOrderBuilders != null) {
                for (Order.Builder builder : mOrderBuilders) {
                    orders.add(builder.build(ratings));
                }
            }

            return new ContentRatingSystem(mName, mDomain, mTitle, mDescription, mCountries,
                    displayName, ratings, subRatings, orders, mIsCustom);
        }
    }

    public static class Rating {
        private final String mName;
        private final String mTitle;
        private final String mDescription;
        private final Drawable mIcon;
        private final int mContentAgeHint;
        private final List<SubRating> mSubRatings;

        public String getName() {
            return mName;
        }

        public String getTitle() {
            return mTitle;
        }

        public String getDescription() {
            return mDescription;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public int getAgeHint() {
            return mContentAgeHint;
        }

        public List<SubRating> getSubRatings() {
            return mSubRatings;
        }

        private Rating(String name, String title, String description, Drawable icon,
                int contentAgeHint, List<SubRating> subRatings) {
            mName = name;
            mTitle = title;
            mDescription = description;
            mIcon = icon;
            mContentAgeHint = contentAgeHint;
            mSubRatings = subRatings;
        }

        public static class Builder {
            private String mName;
            private String mTitle;
            private String mDescription;
            private Drawable mIcon;
            private int mContentAgeHint = -1;
            private final List<String> mSubRatingNames = new ArrayList<>();

            public Builder() {
            }

            public void setName(String name) {
                mName = name;
            }

            public void setTitle(String title) {
                mTitle = title;
            }

            public void setDescription(String description) {
                mDescription = description;
            }

            public void setIcon(Drawable icon) {
                mIcon = icon;
            }

            public void setContentAgeHint(int contentAgeHint) {
                mContentAgeHint = contentAgeHint;
            }

            public void addSubRatingName(String subRatingName) {
                mSubRatingNames.add(subRatingName);
            }

            private Rating build(List<SubRating> allDefinedSubRatings) {
                if (TextUtils.isEmpty(mName)) {
                    throw new IllegalArgumentException("A rating should have non-empty name");
                }
                if (allDefinedSubRatings == null && mSubRatingNames.size() > 0) {
                    throw new IllegalArgumentException("Invalid subrating for rating " + mName);
                }
                if (mContentAgeHint < 0) {
                    throw new IllegalArgumentException("Rating " + mName + " should define " +
                        "non-negative contentAgeHint");
                }

                List<SubRating> subRatings = new ArrayList<>();
                for (String subRatingId : mSubRatingNames) {
                    boolean found = false;
                    for (SubRating subRating : allDefinedSubRatings) {
                        if (subRatingId.equals(subRating.getName())) {
                            found = true;
                            subRatings.add(subRating);
                            break;
                        }
                    }
                    if (!found) {
                        throw new IllegalArgumentException("Unknown subrating name " + subRatingId +
                                " in rating " + mName);
                    }
                }
                return new Rating(
                        mName, mTitle, mDescription, mIcon, mContentAgeHint, subRatings);
            }
        }
    }

    public static class SubRating {
        private final String mName;
        private final String mTitle;
        private final String mDescription;
        private final Drawable mIcon;

        public String getName() {
            return mName;
        }

        public String getTitle() {
            return mTitle;
        }

        public String getDescription() {
            return mDescription;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        private SubRating(String name, String title, String description, Drawable icon) {
            mName = name;
            mTitle = title;
            mDescription = description;
            mIcon = icon;
        }

        public static class Builder {
            private String mName;
            private String mTitle;
            private String mDescription;
            private Drawable mIcon;

            public Builder() {
            }

            public void setName(String name) {
                mName = name;
            }

            public void setTitle(String title) {
                mTitle = title;
            }

            public void setDescription(String description) {
                mDescription = description;
            }

            public void setIcon(Drawable icon) {
                mIcon = icon;
            }

            private SubRating build() {
                if (TextUtils.isEmpty(mName)) {
                    throw new IllegalArgumentException("A subrating should have non-empty name");
                }
                return new SubRating(mName, mTitle, mDescription, mIcon);
            }
        }
    }

    public static class Order {
        private final List<Rating> mRatingOrder;

        public List<Rating> getRatingOrder() {
            return mRatingOrder;
        }

        private Order(List<Rating> ratingOrder) {
            mRatingOrder = ratingOrder;
        }

        /**
         * Returns index of the rating in this order.
         * Returns -1 if this order doesn't contain the rating.
         */
        public int getRatingIndex(Rating rating) {
            for (int i = 0; i < mRatingOrder.size(); i++) {
                if (mRatingOrder.get(i).getName().equals(rating.getName())) {
                    return i;
                }
            }
            return -1;
        }

        public static class Builder {
            private final List<String> mRatingNames = new ArrayList<>();

            public Builder() {
            }

            private Order build(List<Rating> ratings) {
                List<Rating> ratingOrder = new ArrayList<>();
                for (String ratingName : mRatingNames) {
                    boolean found = false;
                    for (Rating rating : ratings) {
                        if (ratingName.equals(rating.getName())) {
                            found = true;
                            ratingOrder.add(rating);
                            break;
                        }
                    }

                    if (!found) {
                        throw new IllegalArgumentException("Unknown rating " + ratingName +
                                " in rating-order tag");
                    }
                }

                return new Order(ratingOrder);
            }

            public void addRatingName(String name) {
                mRatingNames.add(name);
            }
        }
    }
}
