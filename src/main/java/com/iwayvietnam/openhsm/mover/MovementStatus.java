/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zm OpenHSM is the the Hierarchical Storage Management extension for Zimbra Collaboration Open Source Edition..
 * Copyright (C) 2026-present iWay Vietnam and/or its affiliates. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 * ***** END LICENSE BLOCK *****
 *
 * Zimbra OpenHSM
 *
 * Written by Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
package com.iwayvietnam.openhsm.mover;

/**
 * Movement Status
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MovementStatus {
    private final MovedItem movedItem;
    private Status status;

    public MovementStatus(MovedItem movedItem) {
        this.movedItem = movedItem;
    }

    public MovementStatus(MovedItem movedItem, Status status) {
        this.movedItem = movedItem;
        this.status = status;
    }

    public MovedItem getMovedItem() {
        return movedItem;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
