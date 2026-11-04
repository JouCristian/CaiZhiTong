import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import type { ReactNode } from "react";
import { request } from "./api";
interface SystemInfo {
  systemName: string;
  systemDescription: string;
  businessZone: string;
  version: string;
}
const defaults: SystemInfo = {
  systemName: "材智通",
  systemDescription: "智慧材料库数字化管理系统",
  businessZone: "Asia/Shanghai",
  version: "0.7.0",
};
const Context = createContext({ info: defaults, refresh: async () => {} });
export function SystemProvider({ children }: { children: ReactNode }) {
  const [info, setInfo] = useState(defaults);
  const refresh = useCallback(async () => {
    try {
      setInfo(await request<SystemInfo>("/api/public/system", {}, false));
    } catch {
      /* Brand remains available while the API reconnects. */
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return (
    <Context.Provider value={{ info, refresh }}>{children}</Context.Provider>
  );
}
export const useSystem = () => useContext(Context);
