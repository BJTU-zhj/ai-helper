export interface RawSseEvent {
  event: string;
  data: string;
}

export class SseParser {
  private buffer = "";

  push(chunk: string): RawSseEvent[] {
    if (!chunk) {
      return [];
    }
    this.buffer += chunk.replace(/\r\n/g, "\n");
    return this.extractEvents(false);
  }

  flush(): RawSseEvent[] {
    return this.extractEvents(true);
  }

  private extractEvents(flushRemainder: boolean): RawSseEvent[] {
    const events: RawSseEvent[] = [];
    let delimiterIndex = this.buffer.indexOf("\n\n");

    while (delimiterIndex >= 0) {
      const block = this.buffer.slice(0, delimiterIndex);
      this.buffer = this.buffer.slice(delimiterIndex + 2);
      const event = this.parseBlock(block);
      if (event) {
        events.push(event);
      }
      delimiterIndex = this.buffer.indexOf("\n\n");
    }

    if (flushRemainder && this.buffer.trim().length > 0) {
      const event = this.parseBlock(this.buffer);
      this.buffer = "";
      if (event) {
        events.push(event);
      }
    }

    return events;
  }

  private parseBlock(block: string): RawSseEvent | null {
    const lines = block.split("\n");
    let eventName = "message";
    const dataLines: string[] = [];

    for (const line of lines) {
      if (!line || line.startsWith(":")) {
        continue;
      }

      const separatorIndex = line.indexOf(":");
      const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
      const rawValue = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : "";
      const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

      if (field === "event") {
        eventName = value || "message";
      }
      if (field === "data") {
        dataLines.push(value);
      }
    }

    if (dataLines.length === 0 && eventName === "message") {
      return null;
    }

    return {
      event: eventName,
      data: dataLines.join("\n")
    };
  }
}

export function parseMaybeJson(rawData: string): unknown {
  if (!rawData) {
    return null;
  }
  try {
    return JSON.parse(rawData);
  } catch {
    return rawData;
  }
}
