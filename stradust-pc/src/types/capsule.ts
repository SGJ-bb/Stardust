/** 时光胶囊 */
export interface TimeCapsule {
  id: string;
  personaId: string;
  title: string;
  content: string;
  /** 附件 */
  attachments: string[];
  /** 封存时间 */
  sealedAt: number;
  /** 开启时间 */
  openAt: number;
  /** 是否已开启 */
  opened: boolean;
  /** 开启时的消息 */
  openMessage?: string;
  createdAt: number;
}
