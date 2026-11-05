import { Component, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useAmbientMotion } from './useAmbientMotion'
import ParticleText from './reactbits/ParticleText'
import TextType from './reactbits/TextType'
import CurvedLoop from './reactbits/CurvedLoop'
import DitherVeil from './reactbits/DitherVeil'

const mark = '/brand/material-mark-3d.svg'
class VisualFallback extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() { return this.state.failed ? null : this.props.children }
}
function MaterialVeil({ animate }: { animate: boolean }) {
  const [supported, setSupported] = useState(false)
  useEffect(() => {
    if (!animate) return
    try {
      const gl = document.createElement('canvas').getContext('webgl2', { failIfMajorPerformanceCaveat: true })
      setSupported(Boolean(gl))
      gl?.getExtension('WEBGL_lose_context')?.loseContext()
    } catch { setSupported(false) }
  }, [animate])
  return <div className="material-veil" aria-hidden="true">
    <img className="material-veil-fallback" src={mark} alt="" width={800} height={560} />
    {animate && supported && <VisualFallback><DitherVeil src={mark} fit="contain" pattern="bayer" pixelSize={2} palette="duotone" inkColor="#1450a2" paperColor="#f2f5fa" contrast={1.05} brightness={.07} levels={4} revealRadius={110} softness={.65} linger={1.1} rimColor="#6aa7ef" rim={.15} wander={false} clickBurst={false} /></VisualFallback>}
  </div>
}
export function LoginStory() {
  const animate = useAmbientMotion()
  return <>
    <div className="login-story-content">
      <h1 className="login-art-heading" aria-label="连接材料，也连接每一次探索。"><span className="login-particle-shell"><span className="login-particle-fallback">连接材料</span>{animate && <VisualFallback><ParticleText text="连接材料" fontSize="clamp(52px, 5.2vw, 78px)" fontWeight={650} color="#132847" highlightColor="#0066d6" particleSize={1.1} density={2.1} gatherDuration={1150} scatter={90} pointerRepel={16} repelRadius={70} idleDrift={0} clickBurst={false} glow={false} className="login-title-particles" /></VisualFallback>}</span><span className="login-heading-second">也连接每一次探索。</span></h1>
      <div className="login-story-caption">{animate ? <TextType text={['让每一份材料，各得其所。', '从材料档案，连接实验现场。', '让探索有序发生。']} typingSpeed={85} deletingSpeed={45} pauseDuration={3400} initialDelay={600} cursorStyle="bar" cursorColor="#0066d6" typeEffect="fade" startOnVisible /> : <span>让每一份材料，各得其所。</span>}</div>
      <MaterialVeil animate={animate} />
      <p className="login-visual-note">档案、库存与实验现场，在这里有序连接。</p>
    </div>
    <div className="login-curve" aria-hidden="true">{animate ? <CurvedLoop marqueeText="材料有序 · 探索无界 · CAIZHITONG · " speed={.45} curveAmount={26} fontSize={44} fontWeight={450} color="#6686b1" edgeFade={.18} interactive={false} scrollBoost={0} /> : <span>材料有序 · 探索无界</span>}</div>
  </>
}
