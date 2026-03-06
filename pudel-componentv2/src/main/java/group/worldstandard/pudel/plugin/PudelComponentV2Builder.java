/*
 * Basic Pudel - Component V2 Builder
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.plugin;

import group.worldstandard.pudel.api.annotation.Plugin;

/**
 * Unified Component v2 builder Plugin for Pudel Discord Bot
 * <p>
 * Single {@code /component} command opens a Components v2 "Component v2 Builder" with:
 * <ul>
 *   <li>Single slash command entry point</li>
 *   <li>Live visual preview rendered as Components v2</li>
 *   <li>Button-based editing and posting with channel selection via UI</li>
 *   <li>Mutable message once message was send it can't be recall / edit</li>
 * </ul>
 *
 * @author Zazalng
 * @since 1.0.0
 */
@Plugin(
        name = "Pudel's Component V2 Builder",
        version = "1.0.0",
        author = "Zazalng"
)
public class PudelComponentV2Builder {
}
