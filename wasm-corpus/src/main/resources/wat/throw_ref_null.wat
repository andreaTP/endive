(module
  ;; `throw_ref` on a null exception reference traps.

  (func (export "throw-null")
    (ref.null exn)
    (throw_ref)
  )

  ;; reached only if the null case is caught, which it must not be
  (func (export "throw-null-in-try") (result i32)
    (block $h (result (ref exn))
      (try_table (catch_all_ref $h)
        (ref.null exn)
        (throw_ref)
      )
      (return (i32.const 0))
    )
    (drop)
    (i32.const 1)
  )
)
